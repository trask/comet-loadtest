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
public class ServerNode {

    private static final Logger logger = LoggerFactory.getLogger(ServerNode.class);

    private static final String JETTY_VERSION = "8.0.4.v20111024";
    private static final String JETTY_DOWNLOAD_URL = "http://download.eclipse.org/jetty/"
            + JETTY_VERSION + "/dist/jetty-distribution-" + JETTY_VERSION + ".tar.gz";
    private static final String COMET_LOADTEST_SERVER_WAR =
            "comet-loadtest-server-1.0-SNAPSHOT.war";

    private final Ec2Service ec2Service;
    private final String keyPairName;
    private final String privateKeyPath;
    private final String localCookbooksPath;

    private Instance instance;
    private SshSession ssh;

    public ServerNode(Ec2Service ec2Service, String keyPairName, String privateKeyPath,
            String localCookbooksPath) {

        this.ec2Service = ec2Service;
        this.keyPairName = keyPairName;
        this.privateKeyPath = privateKeyPath;
        this.localCookbooksPath = localCookbooksPath;
    }

    public void init() throws IOException, InterruptedException, TimeoutException {
        instance = ec2Service.getOrCreateInstance("CometLoadTestServer",
                Ec2Service.UBUNTU_NATTY_32BIT_AMI_ID, Ec2Service.M1_SMALL_INSTANCE_TYPE,
                "default", keyPairName);
        ec2Service.waitForStartup(instance.getInstanceId(), TimeUnit.MINUTES.toMillis(5));
        instance = ec2Service.getInstance(instance.getInstanceId());
        logger.info("server node public dns name: {}", instance.getPublicDnsName());
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
        cookbooks.add("java");
        // stuff actually required for running load test
        cookbooks.add("java");
        cookbooks.add("limits");
        ChefBootstrap.bootstrap(ssh, localCookbooksPath, cookbooks, "chef-server.json");
        // need to bump up the available port range
        // to handle all of the outgoing comet connections
        ssh.exec("echo '16384 65535' | sudo tee /proc/sys/net/ipv4/ip_local_port_range");
        // install jetty
        installJetty(ssh, "~/jetty");
        // after modifying limits, we have to reconnect for
        // the new limits to take effect in our ssh session
        ssh.disconnect();
        ssh = SshSessionFactory.connect("ubuntu", instance.getPublicDnsName(),
                TimeUnit.MINUTES.toMillis(5), privateKeyPath);
    }

    public void upload() throws NoSuchAlgorithmException, IOException {
        // transfer and install the comet server
        ssh.scp("../comet-loadtest-server/target/" + COMET_LOADTEST_SERVER_WAR);
        ssh.exec("./jetty/bin/jetty.sh stop");
        ssh.exec("cp " + COMET_LOADTEST_SERVER_WAR + " jetty/webapps/comet-loadtest-server.war");
    }

    public void restartJettyServer() throws IOException {
        ssh.exec("./jetty/bin/jetty.sh stop");
        ssh.exec("./jetty/bin/jetty.sh start");
    }

    public String getPrivateDnsName() {
        return instance.getPrivateDnsName();
    }

    public void tearDown(boolean terminateInstance) throws IOException {
        ssh.disconnect();
        if (terminateInstance) {
            ec2Service.terminateInstanceForId(instance.getInstanceId());
        }
    }

    // TODO turn this into a jetty chef cookbook
    private void installJetty(SshSession ssh, String installDir) throws IOException {
        ssh.exec("rm -rf " + installDir);
        ssh.exec("wget -O jetty.tar.gz " + JETTY_DOWNLOAD_URL);
        ssh.exec("tar zxf jetty.tar.gz");
        ssh.exec("mv jetty-distribution-" + JETTY_VERSION + " " + installDir);
    }
}
