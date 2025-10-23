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
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.SearchScope;
import org.axonframework.queryhandling.QueryBus;

import static org.axonframework.configuration.DecoratorDefinition.forType;

/**
 * Configuration enhancer for the {@link DistributedQueryBus}, which upon detection of a {@link QueryBusConnector}
 * in the configuration will decorate the regular {@link org.axonframework.queryhandling.QueryBus} with the provided
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
                    .map(connector -> (QueryBus) new DistributedQueryBus(
                            delegate, connector, queryBusConfiguration
                    ))
                    .orElse(delegate);
        };
    }
}
