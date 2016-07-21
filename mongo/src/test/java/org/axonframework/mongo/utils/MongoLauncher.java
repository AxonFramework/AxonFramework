/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.utils;

import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.*;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.extract.ITempNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
public class MongoLauncher {

    public static final int MONGO_DEFAULT_PORT = 27017;
    public static final String LOCALHOST = "127.0.0.1";
    private static final Logger logger = LoggerFactory.getLogger(MongoLauncher.class);
    private static final AtomicInteger counter = new AtomicInteger();

    private static boolean isMongoRunning() {
        try {
            final Socket mongoSocket = SocketFactory.getDefault().createSocket(LOCALHOST, MONGO_DEFAULT_PORT);

            if (mongoSocket.isConnected()) {
                mongoSocket.close();
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static MongodExecutable prepareExecutable() throws IOException {
        if (isMongoRunning()) {
            return mock(MongodExecutable.class);
        }

        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(MONGO_DEFAULT_PORT, false))
                .build();

        Command command = Command.MongoD;
        IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                .defaults(command)
                .artifactStore(new ArtifactStoreBuilder()
                        .defaults(command)
                        .download(new DownloadConfigBuilder()
                                .defaultsForCommand(command))
                        .executableNaming(new ITempNaming() {
                            @Override
                            public String nameFor(String prefix, String postfix) {
                                return prefix + "_axontest_" + counter.getAndIncrement() + "_" + postfix;
                            }
                        }))
                .build();

        MongodStarter runtime = MongodStarter.getInstance(runtimeConfig);

        return runtime.prepare(mongodConfig);
    }
}
