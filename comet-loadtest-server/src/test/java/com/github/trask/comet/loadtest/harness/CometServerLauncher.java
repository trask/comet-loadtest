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
package com.github.trask.comet.loadtest.harness;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * @author Trask Stalnaker
 */
public class CometServerLauncher {

    private final int port;
    private Server server;

    public CometServerLauncher(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        server = new Server(port);
        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setContextPath("/");
        webAppContext.setResourceBase("src/main/webapp");
        server.setHandler(webAppContext);
        server.setStopAtShutdown(true);
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public int getServletPort() {
        return port;
    }
}
