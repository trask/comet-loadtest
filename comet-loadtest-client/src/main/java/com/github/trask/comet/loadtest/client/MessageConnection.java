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
package com.github.trask.comet.loadtest.client;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

/**
 * @author Trask Stalnaker
 */
public class MessageConnection implements Runnable {

    private final static AtomicLong messageCounter = new AtomicLong();

    private final String messageUrl;
    private final DataCollector dataCollector;
    private final AsyncHttpClient asyncHttpClient;
    private final ExecutorService executorService;

    private volatile long startMillis;
    private volatile String message;
    private volatile ListenableFuture<Response> listenableFuture;

    public MessageConnection(String cometUrl, DataCollector dataCollector,
            AsyncHttpClient asyncHttpClient, ExecutorService executorService) {

        this.messageUrl = cometUrl;
        this.dataCollector = dataCollector;
        this.asyncHttpClient = asyncHttpClient;
        this.executorService = executorService;
    }

    @Override
    public void run() {
        try {
            runInternal();
        } catch (InterruptedException e) {
            dataCollector.collectError(e);
        } catch (ExecutionException e) {
            dataCollector.collectError(e);
        } catch (IOException e) {
            dataCollector.collectError(e);
        }
    }

    public void terminate() {
        listenableFuture.abort(new AbortException());
    }

    private void runInternal() throws InterruptedException, ExecutionException, IOException {
        if (listenableFuture == null) {
            startMillis = System.currentTimeMillis();
            sendMessage();
            dataCollector.messageSent();
        } else {
            verifyCompletedRequest();
            dataCollector.messageResponse(System.currentTimeMillis() - startMillis);
        }
    }

    private void sendMessage() throws IOException {
        message = Long.toString(messageCounter.getAndIncrement());
        BoundRequestBuilder request =
                asyncHttpClient.prepareGet(messageUrl + "?message=" + message);
        listenableFuture = request.execute();
        listenableFuture.addListener(this, executorService);
    }

    private void verifyCompletedRequest() throws InterruptedException, ExecutionException,
            IOException {

        // TODO handle server side / firewall timeout
        Response response = listenableFuture.get();
        String body = response.getResponseBody();
        if (!body.equals(message)) {
            dataCollector.collectError(new IllegalStateException("recieved message '"
                    + body + "' but expecting message '" + message + "'"));
        }
    }

    @SuppressWarnings("serial")
    public static final class AbortException extends Exception {
        private AbortException() {}
    }
}
