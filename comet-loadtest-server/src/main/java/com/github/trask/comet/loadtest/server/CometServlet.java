/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.trask.comet.loadtest.server;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Trask Stalnaker
 */
@SuppressWarnings("serial")
public class CometServlet extends HttpServlet {

    private static final int COMET_ASYNC_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(300);
    private static final int MESSAGE_ASYNC_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(30);

    private static final String MESSAGE_ID = "MESSAGE_ID";

    private static final Logger logger = LoggerFactory.getLogger(CometServlet.class);

    private static final Queue<AsyncContext> cometAsyncContexts =
            new ConcurrentLinkedQueue<AsyncContext>();
    private static final ConcurrentMap<Long, AsyncContext> messageAsyncContexts =
            new ConcurrentHashMap<Long, AsyncContext>();
    private static final AtomicLong nextMessageId = new AtomicLong();
    private static final CometAsyncListener cometAsyncListener = new CometAsyncListener();
    private static final MessageAsyncListener messageAsyncListener = new MessageAsyncListener();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        logger.debug("doGet(): request.pathInfo={}", request.getPathInfo());
        if (request.getPathInfo().equals("/comet")) {
            doComet(request);
        } else if (request.getPathInfo().equals("/message")) {
            doMessage(request, response);
        } else if (request.getPathInfo().equals("/count")) {
            response.getWriter().print(cometAsyncContexts.size());
        } else {
            logger.error("doGet(): unexpected path info {}", request.getPathInfo());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    // this is just for unit tests which re-launch the servlet for each test
    // ideally would use guice-servlet, but trying to reduce complexity
    // of this benchmark, so leaving out guice and using statics
    @Override
    public void destroy() {
        cometAsyncContexts.clear();
        messageAsyncContexts.clear();
        nextMessageId.set(0);
    }

    private void doComet(HttpServletRequest request) throws IOException {
        logger.debug("doComet()");
        String pingback = request.getParameter("pingback");
        if (pingback != null) {
            doCometPingback(pingback);
        }
        AsyncContext cometAsyncContext = request.startAsync();
        cometAsyncContext.setTimeout(COMET_ASYNC_TIMEOUT);
        cometAsyncContext.addListener(cometAsyncListener);
        cometAsyncContexts.add(cometAsyncContext);
    }

    private void doCometPingback(String pingback) throws IOException {
        logger.debug("doCometPingback(): pingback={}", pingback);
        String[] pingbackParts = pingback.split(":");
        long messageId = Long.parseLong(pingbackParts[0]);
        String clientMessage = pingbackParts[1];
        logger.debug("doCometPingback(): messageId={}, clientMessage={}", messageId, clientMessage);
        AsyncContext messageAsyncContext = messageAsyncContexts.remove(messageId);
        if (messageAsyncContext == null) {
            logger.warn("message async request timed out, cannot send response");
        } else {
            messageAsyncContext.getResponse().getWriter().print(clientMessage);
            messageAsyncContext.complete();
        }
    }

    private void doMessage(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        logger.info("doMessage()");
        AsyncContext cometAsyncContext = cometAsyncContexts.poll();
        if (cometAsyncContext == null) {
            logger.error("doMessage(): no comet connection available");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        long messageId = nextMessageId.getAndIncrement();
        String clientMessage = request.getParameter("message");
        logger.debug("doMessage(): messageId={}, clientMessage={}", messageId, clientMessage);
        AsyncContext messageAsyncContext = request.startAsync();
        // stash message id into request so it can be retrieved async listener in case of timeout
        request.setAttribute(MESSAGE_ID, messageId);
        messageAsyncContext.setTimeout(MESSAGE_ASYNC_TIMEOUT);
        messageAsyncContext.addListener(messageAsyncListener);
        messageAsyncContexts.put(messageId, messageAsyncContext);
        cometAsyncContext.getResponse().getWriter().print(messageId + ":" + clientMessage);
        try {
            logger.debug("doMessage(): calling cometAsyncContext.complete()");
            cometAsyncContext.complete();
            logger.debug("doMessage(): cometAsyncContext.complete() ");
        } catch (IllegalStateException e) {
            // trying to triangulate async issue
            logger.error("doMessage(): error calling cometAsyncContext.complete()");
            try {
                Thread.sleep(4000);
            } catch (InterruptedException f) {
                throw new IllegalStateException(f);
            }
            logger.debug("doMessage(): calling cometAsyncContext.complete() x2");
            cometAsyncContext.complete();
        }
    }

    private static class CometAsyncListener implements AsyncListener {
        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            logger.debug("onComplete()");
        }
        @Override
        public void onError(AsyncEvent event) throws IOException {
            logger.error("onError()");
        }
        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            logger.debug("onStartAsync()");
        }
        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            logger.info("onTimeout()");
            if (cometAsyncContexts.remove(event.getAsyncContext())) {
                logger.info("comet async request timed out");
                event.getSuppliedResponse().getWriter().print("TIMEOUT");
                event.getAsyncContext().complete();
            } else {
                logger.warn("interesting?");
            }
        }
    }

    private static class MessageAsyncListener implements AsyncListener {
        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            logger.debug("onComplete()");
        }
        @Override
        public void onError(AsyncEvent event) throws IOException {
            logger.error("onError()");
        }
        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            logger.debug("onStartAsync()");
        }
        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            logger.debug("onTimeout()");
            long messageId = (Long) event.getSuppliedRequest().getAttribute(MESSAGE_ID);
            AsyncContext messageAsyncContext = messageAsyncContexts.remove(messageId);
            if (messageAsyncContext == null) {
                // ok, probably just picked up by doCometPingback
            } else {
                logger.warn("message async request timed out");
                event.getSuppliedResponse().getWriter().print("TIMEOUT");
                event.getAsyncContext().complete();
            }
        }
    }
}
