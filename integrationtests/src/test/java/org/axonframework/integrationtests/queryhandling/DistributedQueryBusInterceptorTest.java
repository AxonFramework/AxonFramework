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

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.integrationtests.testsuite.SharedAxonServerContainer;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.BeforeAll;

/**
 * Implementation of {@link AbstractQueryBusInterceptorTestSuite} that tests interceptor behavior with
 * {@link org.axonframework.queryhandling.distributed.DistributedQueryBus}.
 * <p>
 * This test demonstrates that dispatch interceptors registered via configuration are NOT invoked when using
 * DistributedQueryBus with Axon Server, while handler interceptors ARE properly invoked.
 * <p>
 * The test uses a shared Axon Server container via testcontainers for realistic distributed query bus testing.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DistributedQueryBusInterceptorTest extends AbstractQueryBusInterceptorTestSuite {

    private static final AxonServerContainer container = SharedAxonServerContainer.getInstance();

    @BeforeAll
    static void startContainer() {
        container.start();
    }

    private final AxonConfiguration config = createConfiguration();

    private AxonConfiguration createConfiguration() {
        AxonServerConfiguration axonServerConfig = new AxonServerConfiguration();
        axonServerConfig.setServers(container.getHost() + ":" + container.getGrpcPort());

        return MessagingConfigurer.create()
                                   .componentRegistry(cr -> cr.registerComponent(
                                           AxonServerConfiguration.class, c -> axonServerConfig))
                                   .registerQueryDispatchInterceptor(c -> dispatchInterceptor)
                                   .registerQueryHandlerInterceptor(c -> handlerInterceptor)
                                   .build();
    }

    @Override
    protected AxonConfiguration configuration() {
        return config;
    }
}
