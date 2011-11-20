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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

/**
 * @author Trask Stalnaker
 */
public class CometConnection implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CometConnection.class);

    private final String cometUrl;
    private final DataCollector dataCollector;
    private final AsyncHttpClient asyncHttpClient;
    private final ExecutorService executorService;

    private volatile ListenableFuture<Response> listenableFuture;

    public CometConnection(String cometUrl, DataCollector dataCollector,
            AsyncHttpClient asyncHttpClient, ExecutorService executorService) {

        this.cometUrl = cometUrl;
        this.dataCollector = dataCollector;
        this.asyncHttpClient = asyncHttpClient;
        this.executorService = executorService;
    }

    @Override
    public void run() {
        try {
            runInternal();
        } catch (IOException e) {
            dataCollector.collectError(e);
        } catch (InterruptedException e) {
            dataCollector.collectError(e);
        } catch (ExecutionException e) {
            dataCollector.collectError(e);
        }
    }

    public void runInternal() throws IOException, InterruptedException, ExecutionException {
        if (listenableFuture == null) {
            establishCometConnection();
            dataCollector.cometConnectionEstablished();
        } else if (listenableFuture.isCancelled()) {
            try {
                listenableFuture.get();
                establishCometConnection();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ManualTerminationException) {
                    // test has been terminated
                    return;
                }
                logger.error(e.getMessage(), e);
                establishCometConnection();
            }
        } else {
            Response response = listenableFuture.get();
            logger.debug("response.statusCode={}", response.getStatusCode());
            if (response.getStatusCode() == 200) {
                logger.debug("response.body={}", response.getResponseBody());
                String message = response.getResponseBody();
                if ("TIMEOUT".equals(message)) {
                    establishCometConnection();
                } else {
                    logger.info("received server message {}", message);
                    dataCollector.cometResponse();
                    establishCometConnection("?pingback=" + message);
                }
            } else {
                logger.error("Unexpected comet response status code {}", response.getStatusCode());
                dataCollector.collectError(new IllegalStateException(
                        "Unexpected comet response status code " + response.getStatusCode()));
                establishCometConnection();
            }
        }
    }

    public void terminate() {
        listenableFuture.abort(new ManualTerminationException());
    }

    private void establishCometConnection() throws IOException {
        establishCometConnection(null);
    }

    private void establishCometConnection(String queryString) throws IOException {
        String url = queryString == null ? cometUrl : cometUrl + queryString;
        BoundRequestBuilder request = asyncHttpClient.prepareGet(url);
        listenableFuture = request.execute();
        listenableFuture.addListener(this, executorService);
    }

    @SuppressWarnings("serial")
    private static class ManualTerminationException extends Exception {}
}
