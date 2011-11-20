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
package com.github.trask.comet.loadtest.aws;

/**
 * @author Trask Stalnaker
 */
public final class Main {

    private Main() {}

    public static void main(String... args) throws Exception {
        int nConnections = getIntProperty("connections", 8000);
        int nMessages = getIntProperty("messages", 1000);
        int connectionThrottleMillis = getIntProperty("connectionThrottle", 10);
        int messageThrottleMillis = getIntProperty("messageThrottle", 10);
        CometLoadTest benchmark = new CometLoadTest();
        benchmark.parallelInit();
        benchmark.parallelBootstrap();
        benchmark.parallelUpload();
        benchmark.runLoadTest(nConnections, nMessages, connectionThrottleMillis,
                messageThrottleMillis);
        // pass true to terminate AWS instances (e.g. to avoid AWS charges)
        benchmark.tearDown(false);
    }

    private static int getIntProperty(String propertyName, int defaultValue) {
        String valueText = System.getProperty(propertyName);
        if (valueText == null || valueText.length() == 0) {
            return defaultValue;
        } else {
            return Integer.parseInt(valueText);
        }
    }
}
