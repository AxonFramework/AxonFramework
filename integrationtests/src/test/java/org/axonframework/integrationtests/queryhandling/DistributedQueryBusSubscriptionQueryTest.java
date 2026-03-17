/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBus;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * An {@link AbstractSubscriptionQueryTestSuite} implementation validating the
 * {@link DistributedQueryBus}.
 *
 * @author Mateusz Nowak
 * @author Milan Savic
 * @author Steven van Beelen
 */
@Testcontainers
public class DistributedQueryBusSubscriptionQueryTest extends AbstractSubscriptionQueryTestSuite {

    protected static final Logger logger = LoggerFactory.getLogger(DistributedQueryBusSubscriptionQueryTest.class);

    private static final AxonServerContainer container = new AxonServerContainer(
            "docker.axoniq.io/axoniq/axonserver:2025.2.0")
            .withAxonServerHostname("localhost")
            .withDevMode(true)
            .withReuse(true);

    @BeforeAll
    static void beforeAll() throws IOException {
        container.start();

        // Mainly needed to create DBC context now:
        AxonServerContainerUtils.purgeEventsFromAxonServer(container.getHost(),
                                                           container.getHttpPort(),
                                                           "default",
                                                           AxonServerContainerUtils.DCB_CONTEXT);
        logger.info("Using Axon Server for integration test. UI is available at http://localhost:{}",
                    container.getHttpPort());
    }

    private static AxonServerConfiguration testContainerAxonServerConfiguration() {
        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(container.getHost() + ":" + container.getGrpcPort());
        return axonServerConfiguration;
    }

    private final Configuration config = createMessagingConfigurer().build();

    @Override
    public QueryBus queryBus() {
        return config.getComponent(QueryBus.class);
    }

    @Override
    protected MessagingConfigurer createMessagingConfigurer() {
        return MessagingConfigurer.create()
                                  .componentRegistry(cr -> cr.registerComponent(
                                          AxonServerConfiguration.class,
                                          c -> testContainerAxonServerConfiguration()
                                  ));
    }

    @Test
    void subscriptionQueryInlinePayloadConversion() throws InterruptedException {
        // given
        CountDownLatch queryHandledLatch = new CountDownLatch(1);
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );

        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        result.setCallback(queryHandledLatch::countDown);
        queryHandledLatch.await();

        await().until(result::hasNextAvailable);

        // then
        QueryMessage handledQueryMessage = queryMessageRef.get();
        assertThat(handledQueryMessage.payloadType())
                .isEqualTo(byte[].class);
        assertThat(handledQueryMessage.payloadAs(String.class))
                .isEqualTo(TEST_QUERY_PAYLOAD);
        QueryResponseMessage firstResult = result.next().orElseThrow().message();
        assertThat(firstResult.payloadType())
                .isEqualTo(byte[].class);
        assertThat(firstResult.payloadAs(String.class))
                .isEqualTo(TEST_RESPONSE_PAYLOAD_1);
    }
}
