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

import org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.MessagingConfigurer;

/**
 * Implementation of {@link AbstractQueryBusInterceptorTestSuite} that tests interceptor behavior with
 * {@link org.axonframework.queryhandling.SimpleQueryBus}.
 * <p>
 * This test demonstrates that dispatch interceptors registered via configuration are NOT invoked when using
 * SimpleQueryBus, while handler interceptors ARE properly invoked.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleQueryBusInterceptorTest extends AbstractQueryBusInterceptorTestSuite {

    private final AxonConfiguration config = MessagingConfigurer.create()
                                                                 .componentRegistry(cr -> cr.disableEnhancer(
                                                                         AxonServerConfigurationEnhancer.class))
                                                                 .registerQueryDispatchInterceptor(c -> dispatchInterceptor)
                                                                 .registerQueryHandlerInterceptor(c -> handlerInterceptor)
                                                                 .build();

    @Override
    protected AxonConfiguration configuration() {
        return config;
    }
}
