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

package org.axonframework.queryhandling.distributed;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.configuration.ComponentDecorator;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.SearchScope;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.interceptors.DispatchInterceptingQueryBus;

import java.util.List;

import static org.axonframework.configuration.DecoratorDefinition.forType;

/**
 * Configuration enhancer for the {@link DistributedQueryBus}, which upon detection of a {@link QueryBusConnector} in
 * the configuration will decorate the regular {@link org.axonframework.queryhandling.QueryBus} with the provided
 * connector.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class DistributedQueryBusConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The order in which the {@link DistributedQueryBus} is applied to the {@link QueryBus} in the
     * {@link ComponentRegistry}. As such, any decorator with a lower value will be applied to the delegate, and any
     * higher value will be applied to the {@link DistributedQueryBus} itself. Using the same value can either lead to
     * application of the decorator to the delegate or the distributed query bus, depending on the order of
     * registration.
     */
    public static final int DISTRIBUTED_QUERY_BUS_ORDER = -1;

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        if (componentRegistry.hasComponent(QueryBusConnector.class)) {
            componentRegistry
                    .registerIfNotPresent(
                            DistributedQueryBusConfiguration.class,
                            (c) -> DistributedQueryBusConfiguration.DEFAULT,
                            SearchScope.ALL
                    )
                    .registerDecorator(forType(QueryBus.class).with(queryBusDecoratorDefinition())
                                                              .order(DISTRIBUTED_QUERY_BUS_ORDER));
        }
    }

    private ComponentDecorator<QueryBus, QueryBus> queryBusDecoratorDefinition() {
        return (config, name, delegate) -> {
            if (delegate instanceof DistributedQueryBus) {
                return delegate;
            }
            var queryBusConfiguration = config.getComponent(DistributedQueryBusConfiguration.class);
            return config.getOptionalComponent(QueryBusConnector.class)
                         .map(connector -> dispatchInterceptingQueryBus(
                                 config,
                                 distributedQueryBus(delegate, connector, queryBusConfiguration))
                         )
                         .orElse(delegate);
        };
    }

    @Nonnull
    private static QueryBus distributedQueryBus(QueryBus delegate, QueryBusConnector connector,
                                                DistributedQueryBusConfiguration queryBusConfiguration) {
        return new DistributedQueryBus(delegate, connector, queryBusConfiguration);
    }

    /**
     * Wraps the given {@code delegate} (typically a {@link DistributedQueryBus}) with a
     * {@link DispatchInterceptingQueryBus} to ensure dispatch interceptors are invoked on all queries.
     * <p>
     * This wrapping is necessary because the {@link DistributedQueryBus} performs all query dispatching directly,
     * bypassing any dispatch interceptors that might be registered on the local segment. While the configuration
     * automatically wraps the local segment with handler interceptors (via
     * {@link org.axonframework.queryhandling.interceptors.HandlerInterceptingQueryBus}), dispatch interceptors must be
     * applied to the {@link DistributedQueryBus} itself to be invoked.
     * <p>
     * Without this wrapping, dispatch interceptors would not be invoked when using a distributed query bus setup, even
     * though they are properly registered in the {@link DispatchInterceptorRegistry}.
     *
     * @param config   The {@link Configuration} providing access to registered dispatch interceptors.
     * @param delegate The {@link QueryBus} to wrap, typically a {@link DistributedQueryBus}.
     * @return The {@code delegate} wrapped with dispatch interceptor support if interceptors are registered, or the
     * {@code delegate} unchanged if no interceptors are present.
     */
    private static QueryBus dispatchInterceptingQueryBus(Configuration config, QueryBus delegate) {
        List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors =
                config.getComponent(DispatchInterceptorRegistry.class).queryInterceptors(config);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> updateDispatchInterceptors =
                config.getComponent(DispatchInterceptorRegistry.class).subscriptionQueryUpdateInterceptors(config);
        return dispatchInterceptors.isEmpty() && updateDispatchInterceptors.isEmpty()
                ? delegate
                : new DispatchInterceptingQueryBus(delegate,
                                                   dispatchInterceptors,
                                                   updateDispatchInterceptors);
    }
}
