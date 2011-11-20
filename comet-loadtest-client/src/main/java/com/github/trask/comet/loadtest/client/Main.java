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
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Trask Stalnaker
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private final String url;
    private final int nConnections;
    private final int nMessages;
    private final int connectionThrottleMillis;
    private final int messageThrottleMillis;

    public Main(String url, int nConnections, int nMessages, int connectionThrottleMillis,
            int messageThrottleMillis) {

        this.url = url;
        this.nConnections = nConnections;
        this.nMessages = nMessages;
        this.connectionThrottleMillis = connectionThrottleMillis;
        this.messageThrottleMillis = messageThrottleMillis;
    }

    public boolean run() throws InterruptedException, IOException, ExecutionException,
            TimeoutException {

        Controller controller = new Controller(url);

        logger.debug("establishing comet connections ...");
        controller.establishCometConnections(nConnections, connectionThrottleMillis);
        controller.waitForConnections(nConnections, 60000);

        logger.debug("sending messages ...");
        controller.sendMessages(nMessages, messageThrottleMillis);
        controller.waitForResponses(nMessages, 60000);

        System.out.println("====================");
        controller.printData();
        System.out.println("====================");

        logger.debug("terminating ...");
        controller.terminate();
        logger.debug("bye");
        return controller.successful();
    }

    public static void main(String... args) throws InterruptedException, IOException,
            ExecutionException, TimeoutException {

        if (args.length > 0) {
            displayHelp();
            System.exit(0);
        }
        String url = System.getProperty("url", "http://localhost:8080/comet-loadtest-server");
        int nConnections = getIntProperty("connections", 1000);
        int nMessages = getIntProperty("messages", 1000);
        int connectionThrottleMillis = getIntProperty("connectionThrottle", 1);
        int messageThrottleMillis = getIntProperty("messageThrottle", 10);
        new Main(url, nConnections, nMessages, connectionThrottleMillis, messageThrottleMillis)
                .run();
    }

    private static int getIntProperty(String propertyName, int defaultValue) {
        String valueText = System.getProperty(propertyName);
        if (valueText == null || valueText.length() == 0) {
            return defaultValue;
        } else {
            return Integer.parseInt(valueText);
        }
    }

    private static void displayHelp() {
        System.out.println("Usage: java " + Main.class.getName() + "");
    }
}
