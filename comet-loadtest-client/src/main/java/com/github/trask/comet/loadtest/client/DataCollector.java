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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Trask Stalnaker
 */
public class DataCollector implements UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DataCollector.class);

    private final AtomicLong cometConnectionEstablishedCount = new AtomicLong();
    private final AtomicLong cometResponseCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong messageSentCount = new AtomicLong();
    private final AtomicLong messageResponseCount = new AtomicLong();
    private final AtomicLong messageResponseTime = new AtomicLong();

    public void cometConnectionEstablished() {
        cometConnectionEstablishedCount.getAndIncrement();
    }

    public void cometResponse() {
        cometResponseCount.getAndIncrement();
    }

    public void messageSent() {
        messageSentCount.getAndIncrement();
    }

    public void messageResponse(long responseTime) {
        messageResponseCount.getAndIncrement();
        messageResponseTime.getAndAdd(responseTime);
        System.out.println(responseTime);
    }

    public void collectError(Throwable t) {
        errorCount.getAndIncrement();
        logger.error(t.getMessage(), t);
    }

    public long getMessageResponseCount() {
        return messageResponseCount.get();
    }

    public boolean successful() {
        return errorCount.get() == 0 && messageSentCount.get() == messageResponseCount.get();
    }

    public void printData() {
        System.out.println("comet connections established: " + cometConnectionEstablishedCount);
        System.out.println("comet responses: " + cometResponseCount);
        System.out.println("error count: " + errorCount);
        System.out.println("message sent count: " + messageSentCount);
        System.out.println("message response count: " + messageResponseCount);
        System.out.format("message average response time: %d milliseconds\n",
                messageResponseTime.get() / messageResponseCount.get());
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        collectError(e);
    }
}
