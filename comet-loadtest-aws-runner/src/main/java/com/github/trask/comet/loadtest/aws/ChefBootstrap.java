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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.github.trask.sandbox.sshj.SshSession;
import com.google.common.io.Files;

/**
 * @author Trask Stalnaker
 */
public class ChefBootstrap {

    public static void bootstrap(SshSession ssh, String localCookbooksPath, List<String> cookbooks,
            String chefJsonConfigurationFile) throws Exception {

        // occasionally I get errors
        // "Unable to locate package ..."
        // maybe we just need to give the instance a few more seconds to start up?
        Thread.sleep(15000);
        // see http://wiki.opscode.com/display/chef/Bootstrap+Chef+RubyGems+Installation
        // and http://wiki.opscode.com/display/chef/Chef+Solo
        ssh.exec("sudo apt-get update");
        ssh.exec("sudo apt-get install -y ruby ruby-dev libopenssl-ruby"
                + " rdoc ri irb build-essential wget ssl-cert");
        ssh.exec("wget http://production.cf.rubygems.org/rubygems/rubygems-1.8.11.tgz");
        ssh.exec("tar zxf rubygems-1.8.11.tgz");
        // let the user know not to panic (TODO investigate underlying issue)
        System.out.println("not sure why, but when rubygems is already installed"
                + " (e.g. this is a second run)");
        System.out.println("the following step hangs for a minute or so (but then is successful)");
        ssh.exec("cd rubygems-1.8.11; sudo ruby setup.rb --no-format-executable");
        ssh.exec("rm -rf rubygems-1.8.11 rubygems-1.8.11.tgz");
        ssh.exec("sudo gem install --no-rdoc --no-ri chef");
        ssh.exec("sudo gem install --no-rdoc --no-ri ohai");
        // transfer solo.rb
        ssh.scp("solo.rb");
        ssh.exec("sudo mkdir -p /etc/chef");
        ssh.exec("sudo mv solo.rb /etc/chef/solo.rb");
        ssh.exec("sudo chown root:root /etc/chef/solo.rb");
        // transfer chef json configuration file
        ssh.scp(chefJsonConfigurationFile);
        // prepare and transfer cookbooks
        prepareCookbook(localCookbooksPath, cookbooks, "target/cookbooks.tar.gz");
        ssh.scp("target/cookbooks.tar.gz");
        ssh.exec("tar zxf cookbooks.tar.gz");
        // this rm -rf is just helpful when re-running (e.g. while debugging)
        ssh.exec("sudo rm -rf /var/chef-solo");
        ssh.exec("sudo mkdir -p /var/chef-solo");
        ssh.exec("sudo mv cookbooks /var/chef-solo");
        ssh.exec("sudo chown -R root:root /var/chef-solo/cookbooks");
        ssh.exec("sudo rm cookbooks.tar.gz");
        // run chef-solo
        ssh.exec("sudo chef-solo -j " + chefJsonConfigurationFile);
    }

    private static void prepareCookbook(String cookbooksDir, List<String> cookbooks,
            String destFilename) throws IOException, FileNotFoundException {

        TarArchiveOutputStream cookbooksTarOut = new TarArchiveOutputStream(
                new GZIPOutputStream(new FileOutputStream(destFilename)));
        for (String cookbook : cookbooks) {
            File cookbookDir = new File(cookbooksDir, cookbook);
            if (!cookbookDir.exists()) {
                throw new IllegalStateException("missing cookbook " + cookbookDir);
            }
            addToTarWithBase(cookbookDir, cookbooksTarOut, "cookbooks/");
        }
        cookbooksTarOut.close();
    }

    private static void addToTarWithBase(File file, TarArchiveOutputStream tarOut, String base)
            throws IOException {

        String entryName = base + file.getName();
        TarArchiveEntry tarEntry = new TarArchiveEntry(file, entryName);

        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        tarOut.putArchiveEntry(tarEntry);

        if (file.isFile()) {
            Files.asByteSource(file).copyTo(tarOut);
            tarOut.closeArchiveEntry();
        } else {
            tarOut.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToTarWithBase(child, tarOut, entryName + "/");
                }
            }
        }
    }
}
