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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.model.Instance;
import com.github.trask.sandbox.ec2.Ec2Service;
import com.github.trask.sandbox.sshj.SshSession;
import com.github.trask.sandbox.sshj.SshSessionFactory;

/**
 * @author Trask Stalnaker
 */
public class ClientNode {

    private static final Logger logger = LoggerFactory.getLogger(ClientNode.class);

    private static final String COMET_LOADTEST_CLIENT_JAR =
            "comet-loadtest-client-1.0-SNAPSHOT-jar-with-dependencies.jar";

    private final Ec2Service ec2Service;
    private final String keyPairName;
    private final String privateKeyPath;
    private final String localCookbooksPath;

    private Instance instance;
    private SshSession ssh;

    public ClientNode(Ec2Service ec2Service, String keyPairName,
            String privateKeyPath, String localCookbooksPath) {

        this.ec2Service = ec2Service;
        this.keyPairName = keyPairName;
        this.privateKeyPath = privateKeyPath;
        this.localCookbooksPath = localCookbooksPath;
    }

    public void init() throws IOException, InterruptedException, TimeoutException {
        instance = ec2Service.getOrCreateInstance("CometLoadTestClient",
                Ec2Service.UBUNTU_NATTY_32BIT_AMI_ID, Ec2Service.M1_SMALL_INSTANCE_TYPE,
                "default", keyPairName);
        ec2Service.waitForStartup(instance.getInstanceId(), TimeUnit.MINUTES.toMillis(5));
        instance = ec2Service.getInstance(instance.getInstanceId());
        logger.info("client node public dns name: {}", instance.getPublicDnsName());
        ssh = SshSessionFactory.connect("ubuntu", instance.getPublicDnsName(),
                TimeUnit.MINUTES.toMillis(5), privateKeyPath);
    }

    public void bootstrap() throws Exception {
        List<String> cookbooks = new ArrayList<String>();
        // linux convenience stuff
        cookbooks.add("timezone");
        cookbooks.add("hostname");
        cookbooks.add("dircolors");
        cookbooks.add("vim");
        // stuff actually required for running load test
        cookbooks.add("java");
        cookbooks.add("limits");
        ChefBootstrap.bootstrap(ssh, localCookbooksPath, cookbooks, "chef-client.json");
        // need to bump up the available port range
        // to handle all of the outgoing comet connections
        ssh.exec("echo '16384 65535' | sudo tee /proc/sys/net/ipv4/ip_local_port_range");
        ssh.disconnect();
        ssh = SshSessionFactory.connect("ubuntu", instance.getPublicDnsName(),
                TimeUnit.MINUTES.toMillis(5), privateKeyPath);
    }

    public void upload() throws NoSuchAlgorithmException, IOException {
        // transfer the load test client application
        ssh.scp("../comet-loadtest-client/target/" + COMET_LOADTEST_CLIENT_JAR);
    }

    public void runLoadTest(int nConnections, int nMessages, int connectionThrottleMillis,
            int messageThrottleMillis, String serverNodePrivateDnsName) throws IOException {

        String url = "http://" + serverNodePrivateDnsName + ":8080/comet-loadtest-server";
        ssh.execVoid("java -Durl=" + url
                + " -Dconnections=" + nConnections
                + " -Dmessages=" + nMessages
                + " -DconnectionThrottle=" + connectionThrottleMillis
                + " -DmessageThrottle=" + messageThrottleMillis
                + " -jar " + COMET_LOADTEST_CLIENT_JAR);
    }

    public void tearDown(boolean terminateInstance) throws IOException {
        ssh.disconnect();
        if (terminateInstance) {
            ec2Service.terminateInstanceForId(instance.getInstanceId());
        }
    }
}
