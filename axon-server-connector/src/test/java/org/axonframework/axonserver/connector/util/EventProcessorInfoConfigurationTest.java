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

package org.axonframework.axonserver.connector.util;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.StubServer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.junit.jupiter.api.*;

import java.io.IOException;

@Disabled("TODO #3521") // Left over to remove
class EventProcessorInfoConfigurationTest {

    private StubServer stubServer;
    private int port;
    private AxonConfiguration configuration;

    @BeforeEach
    void setUp() throws IOException {
        port = TcpUtils.findFreePort();
        stubServer = new StubServer(port);
        stubServer.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (configuration != null) {
            configuration.shutdown();
        }
        stubServer.shutdown();
    }

    @Test
    void noActionShouldBeTakenWhenThereIsNoEventProcessingConfiguration() {
        MessagingConfigurer configurer = MessagingConfigurer.create().componentRegistry(
                cr -> cr.registerComponent(AxonServerConfiguration.class,
                                           c -> AxonServerConfiguration.builder()
                                                                       .servers("localhost:" + port)
                                                                       .connectTimeout(1000)
                                                                       .build())
        );

        configuration = configurer.build();
        Assertions.assertDoesNotThrow(() -> configuration.start());
        configuration.shutdown();
    }
}