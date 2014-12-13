/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.mongoutils;

import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ServerSocketFactory;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class MongoLauncher {

    private static final Logger logger = LoggerFactory.getLogger(MongoLauncher.class);
    private static final AtomicInteger counter = new AtomicInteger();

    public static MongodExecutable prepareExecutable() throws IOException {
        int port = 27017;
        try {
            final ServerSocketFactory socketFactory = ServerSocketFactory.getDefault();
            ServerSocket socket = socketFactory.createServerSocket(port);
            socket.close();
            if (!socket.isClosed()) {
                logger.warn("Didn't manage to close socket for some reason. Tests may start failing...");
            }
        } catch (IOException e) {
            logger.info("Port 27017 is taken. Assuming MongoDB is running");
            // returning a mock, so we can call close on it, which is ignored
            return mock(MongodExecutable.class);
        }

        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(port, false))
                .build();

        Command command = Command.MongoD;
        IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                .defaults(command)
                .artifactStore(new ArtifactStoreBuilder()
                                       .defaults(command)
                                       .download(new DownloadConfigBuilder()
                                                         .defaultsForCommand(command))
                                       .executableNaming((prefix, postfix) -> prefix + "_axontest_" + counter.getAndIncrement() + "_" + postfix))
                .build();

        MongodStarter runtime = MongodStarter.getInstance(runtimeConfig);

        return runtime.prepare(mongodConfig);
    }
}
