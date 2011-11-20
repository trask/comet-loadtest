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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;

/**
 * @author Trask Stalnaker
 */
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    // TODO not entirely sure of the impact of this parameter
    private static final int SCHEDULED_EXECUTOR_SERVICE_CORE_POOL_SIZE = 10;

    private final String url;
    private final AsyncHttpClient asyncHttpClient;
    private final ExecutorService executorService;
    private final DataCollector dataCollector = new DataCollector();

    private final Set<CometConnection> cometConnections =
            Collections.newSetFromMap(new ConcurrentHashMap<CometConnection, Boolean>());
    private final Set<MessageConnection> messageConnections =
            Collections.newSetFromMap(new ConcurrentHashMap<MessageConnection, Boolean>());

    public Controller(String url) {
        this.url = url;
        executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("CometClient-Executor-%d")
                .setUncaughtExceptionHandler(dataCollector)
                .build());
        asyncHttpClient = makeAsyncHttpClient(executorService);
    }

    public void establishCometConnections(int nConnections, int throttleMillis)
            throws InterruptedException {

        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < nConnections; i++) {
            CometConnection cometConnection = new CometConnection(
                    url + "/comet", dataCollector, asyncHttpClient, executorService);
            cometConnections.add(cometConnection);
            executorService.execute(cometConnection);
            Thread.sleep(throttleMillis);
        }
        logger.info("established {} connections in {} milliseconds", nConnections,
                System.currentTimeMillis() - startMillis);
    }

    public void waitForConnections(int nConnections, int timeoutMillis) throws IOException,
            InterruptedException, ExecutionException, TimeoutException {

        long startMillis = System.currentTimeMillis();
        while (true) {
            BoundRequestBuilder request = asyncHttpClient.prepareGet(url + "/count");
            String countText = request.execute().get().getResponseBody();
            int count = Integer.parseInt(countText);
            logger.debug("waitForConnections(): count={}", count);
            if (count >= nConnections) {
                return;
            } else if (System.currentTimeMillis() - startMillis >= timeoutMillis) {
                throw new TimeoutException();
            } else {
                Thread.sleep(100);
            }
        }
    }

    public void waitForResponses(int nMessages, int timeoutMillis) throws IOException,
            InterruptedException, ExecutionException, TimeoutException {

        long startMillis = System.currentTimeMillis();
        while (true) {
            long count = dataCollector.getMessageResponseCount();
            logger.debug("waitForResponses(): count={}", count);
            if (count >= nMessages) {
                return;
            } else if (System.currentTimeMillis() - startMillis >= timeoutMillis) {
                throw new TimeoutException();
            } else {
                Thread.sleep(100);
            }
        }
    }

    public void sendMessages(int nMessages, int throttleMillis) throws InterruptedException {
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < nMessages; i++) {
            MessageConnection messageConnection = new MessageConnection(
                    url + "/message", dataCollector, asyncHttpClient, executorService);
            messageConnections.add(messageConnection);
            executorService.execute(messageConnection);
            Thread.sleep(throttleMillis);
        }
        logger.info("sent {} messages over {} milliseconds",
                nMessages, System.currentTimeMillis() - startMillis);
    }

    public void terminate() {
        for (CometConnection cometConnection : cometConnections) {
            cometConnection.terminate();
        }
        asyncHttpClient.close();
    }

    public void printData() {
        dataCollector.printData();
    }

    public boolean successful() {
        return dataCollector.successful();
    }

    private static AsyncHttpClient makeAsyncHttpClient(ExecutorService executorService) {
        ScheduledExecutorService scheduledExecutorService =
                Executors.newScheduledThreadPool(SCHEDULED_EXECUTOR_SERVICE_CORE_POOL_SIZE,
                        new ThreadFactoryBuilder()
                                .setDaemon(true)
                                .setNameFormat("CometBenchmark-ScheduledExecutor-%d")
                                .build());
        AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder()
                .setAllowPoolingConnection(true)
                .setMaxRequestRetry(0)
                .setRequestTimeoutInMs((int) TimeUnit.MINUTES.toMillis(10))
                .setExecutorService(executorService)
                .setScheduledExecutorService(scheduledExecutorService);
        NettyAsyncHttpProviderConfig providerConfig = new NettyAsyncHttpProviderConfig();
        providerConfig.addProperty(NettyAsyncHttpProviderConfig.BOSS_EXECUTOR_SERVICE,
                executorService);
        builder.setAsyncHttpClientProviderConfig(providerConfig);
        return new AsyncHttpClient(builder.build());
    }
}
