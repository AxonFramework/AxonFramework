/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.testsuite;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Abstract test suite for integration tests using an AxonServerContainer. Concrete implementations have to provide a
 * specific {@link ApplicationConfigurer}. The server is started using the default {@link AxonServerConfiguration}. The
 * started configuration and the associated {@link CommandGateway} are available through member-variables.
 *
 * @author Mitchell Herrijgers
 */
public abstract class AbstractAxonServerIT {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractAxonServerIT.class);

    private static final AxonServerContainer container = new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
            .withAxonServerHostname("localhost")
            .withDevMode(true)
            .withReuse(true)
            .withDcbContext(true);

    protected CommandGateway commandGateway;
    protected AxonConfiguration startedConfiguration;

    @BeforeAll
    static void beforeAll() {
        container.start();
        logger.info("Using Axon Server for integration test. UI is available at http://localhost:{}",
                    container.getHttpPort());
    }

    @AfterEach
    void tearDown() {
        if (startedConfiguration != null) {
            startedConfiguration.shutdown();
        }
    }

    protected void startApp() {
        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(container.getHost() + ":" + container.getGrpcPort());
        startedConfiguration = createConfigurer().componentRegistry(cr -> cr.registerComponent(
                                                         AxonServerConfiguration.class, c -> axonServerConfiguration
                                                 ))
                                                 .start();
        commandGateway = startedConfiguration.getComponent(CommandGateway.class);
    }

    /**
     * Creates the {@link ApplicationConfigurer} defining the Axon Framework test context.
     *
     * @return The {@link ApplicationConfigurer} defining the Axon Framework test context.
     */
    protected abstract ApplicationConfigurer createConfigurer();

    private static final Random RND = new Random();

    protected static String createId(String prefix) {
        return prefix + "-" + RND.nextInt(Integer.MAX_VALUE);
    }
}
