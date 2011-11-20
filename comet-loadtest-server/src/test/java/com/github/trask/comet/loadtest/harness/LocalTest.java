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

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.trask.comet.loadtest.client.Main;

/**
 * @author Trask Stalnaker
 */
public class LocalTest {

    private static final int PORT = 8080;

    private CometServerLauncher launcher;

    @Before
    public void before() throws Exception {
        launcher = new CometServerLauncher(PORT);
        launcher.start();
    }

    @After
    public void after() throws Exception {
        launcher.stop();
    }

    @Test
    public void shouldOpenSingleCometConnectionAndSendAndReceiveSingleMessage() throws Exception {
        String url = "http://localhost:" + PORT;
        boolean successful = new Main(url, 1, 1, 0, 0).run();
        assertTrue(successful);
    }

    @Test
    public void shouldOpenTenCometConnectionsAndSendAndReceiveTenMessages() throws Exception {
        String url = "http://localhost:" + PORT;
        boolean successful = new Main(url, 10, 10, 0, 0).run();
        assertTrue(successful);
    }

    @Test
    public void shouldOpenHundredCometConnectionsAndSendAndReceiveHundredMessages()
            throws Exception {

        String url = "http://localhost:" + PORT;
        boolean successful = new Main(url, 100, 100, 0, 0).run();
        assertTrue(successful);
    }

    @Test
    public void shouldOpenThousandCometConnectionsAndSendAndReceiveThousandMessages()
            throws Exception {

        String url = "http://localhost:" + PORT;
        // with this many messages it helps a lot to throttle them a little
        boolean successful = new Main(url, 1000, 1000, 0, 5).run();
        assertTrue(successful);
    }
}
