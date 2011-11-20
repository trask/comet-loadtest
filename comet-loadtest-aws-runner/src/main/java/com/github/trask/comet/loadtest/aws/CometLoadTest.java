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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.github.trask.sandbox.ec2.Ec2Service;
import com.github.trask.sandbox.executors.DaemonExecutors;

/**
 * @author Trask Stalnaker
 */
// TODO
// add GC parameters to both server and client
// *try* add multiple ip addresses to increase available ports
// not sure if this can be done on EC2
public class CometLoadTest {

    private final ServerNode serverNode;
    private final ClientNode clientNode;

    public CometLoadTest() throws FileNotFoundException, IOException {
        Properties properties = new Properties();
        FileInputStream propertiesIn = new FileInputStream("comet-loadtest.properties");
        try {
            properties.load(propertiesIn);
        } finally {
            propertiesIn.close();
        }
        String accessKeyId = properties.getProperty("aws.accessKeyId");
        String secretAccessKey = properties.getProperty("aws.secretAccessKey");
        AWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
        Ec2Service ec2Service = new Ec2Service(credentials);
        String keyPairName = properties.getProperty("ec2.keyPair.name");
        String privateKeyPath = properties.getProperty("ec2.keyPair.privateKeyPath");
        // see notes in example.sh on how to create chef-repo directory
        String localCookbooksPath = properties.getProperty("chef.localCookbooksPath");

        serverNode = new ServerNode(ec2Service, keyPairName, privateKeyPath, localCookbooksPath);
        clientNode = new ClientNode(ec2Service, keyPairName, privateKeyPath, localCookbooksPath);
    }

    public void parallelInit() throws Exception {
        ExecutorService executor = DaemonExecutors.newCachedThreadPool("Init-Executor");
        Future<Void> serverInitFuture = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                serverNode.init();
                return null;
            }
        });
        Future<Void> clientInitFuture = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                clientNode.init();
                return null;
            }
        });
        serverInitFuture.get(10, TimeUnit.MINUTES);
        clientInitFuture.get(10, TimeUnit.MINUTES);
    }

    public void parallelBootstrap() throws Exception {
        ExecutorService executor = DaemonExecutors.newCachedThreadPool("Bootstrap-Executor");
        Future<Void> serverBootstrapFuture = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                serverNode.bootstrap();
                return null;
            }
        });
        Future<Void> clientBoostrapFuture = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                clientNode.bootstrap();
                return null;
            }
        });
        serverBootstrapFuture.get(10, TimeUnit.MINUTES);
        clientBoostrapFuture.get(10, TimeUnit.MINUTES);
    }

    public void parallelUpload() throws Exception {
        ExecutorService executor = DaemonExecutors.newCachedThreadPool("Upload-Executor");
        Future<Void> serverUploadFuture = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                serverNode.upload();
                return null;
            }
        });
        Future<Void> clientUploadFuture = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                clientNode.upload();
                return null;
            }
        });
        serverUploadFuture.get(10, TimeUnit.MINUTES);
        clientUploadFuture.get(10, TimeUnit.MINUTES);
    }

    public void runLoadTest(int nConnections, int nMessages, int connectionThrottleMillis,
            int messageThrottleMillis) throws Exception {

        serverNode.restartJettyServer();
        // give jetty a few seconds to finish starting
        // TODO don't return from restartJettyServer until it jetty is ready to go
        Thread.sleep(10000);
        clientNode.runLoadTest(nConnections, nMessages, connectionThrottleMillis,
                messageThrottleMillis, serverNode.getPrivateDnsName());
    }

    public void tearDown(boolean terminateInstances) throws IOException {
        serverNode.tearDown(terminateInstances);
        clientNode.tearDown(terminateInstances);
    }
}
