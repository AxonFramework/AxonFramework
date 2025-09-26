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

package org.axonframework.queryhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.SimpleQueryHandlingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.axonframework.configuration.ComponentDefinition.ofTypeAndName;

/**
 * Simple implementation of the {@link SimpleQueryHandlingModule}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleQueryHandlingModule extends BaseModule<SimpleQueryHandlingModule>
        implements QueryHandlingModule,
        QueryHandlingModule.SetupPhase,
        QueryHandlingModule.QueryHandlerPhase {

    private final String queryHandlingComponentName;
    private final Map<QueryHandlerName, ComponentBuilder<QueryHandler>> handlerBuilders;
    private final List<ComponentBuilder<QueryHandlingComponent>> handlingComponentBuilders;

    SimpleQueryHandlingModule(@Nonnull String moduleName) {
        super(requireNonNull(moduleName, "The module name cannot be null."));
        this.queryHandlingComponentName = "QueryHandlingComponent[" + moduleName + "]";
        this.handlerBuilders = new HashMap<>();
        this.handlingComponentBuilders = new ArrayList<>();
    }

    @Override
    public QueryHandlerPhase queryHandlers() {
        return this;
    }

    @Override
    public QueryHandlerPhase queryHandler(@Nonnull QualifiedName queryName,
                                          @Nonnull QualifiedName responseName,
                                          @Nonnull ComponentBuilder<QueryHandler> queryHandlerBuilder) {
        handlerBuilders.put(new QueryHandlerName(queryName, responseName),
                            requireNonNull(queryHandlerBuilder, "The query handler builder cannot be null."));
        return this;
    }

    @Override
    public QueryHandlerPhase queryHandlingComponent(
            @Nonnull ComponentBuilder<QueryHandlingComponent> handlingComponentBuilder
    ) {
        handlingComponentBuilders.add(
                requireNonNull(handlingComponentBuilder, "The query handling component builder cannot be null.")
        );
        return this;
    }

    @Override
    public QueryHandlingModule build() {
        registerQueryHandlingComponent();
        return this;
    }

    private void registerQueryHandlingComponent() {
        componentRegistry(cr -> cr.registerComponent(queryHandlingComponentComponentDefinition()));
    }

    private ComponentDefinition<QueryHandlingComponent> queryHandlingComponentComponentDefinition() {
        return ofTypeAndName(QueryHandlingComponent.class, queryHandlingComponentName)
                .withBuilder(c -> {
                    SimpleQueryHandlingComponent queryHandlingComponent = SimpleQueryHandlingComponent.create(
                            queryHandlingComponentName
                    );
                    handlingComponentBuilders.forEach(handlingComponent -> queryHandlingComponent.subscribe(
                            handlingComponent.build(c)));
                    handlerBuilders.forEach((key, value) -> queryHandlingComponent.subscribe(key, value.build(c)));
                    return queryHandlingComponent;
                })
                .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (configuration, component) -> {
                    configuration.getComponent(QueryBus.class)
                                 .subscribe(configuration.getComponent(QueryHandlingComponent.class,
                                                                       queryHandlingComponentName));
                    return FutureUtils.emptyCompletedFuture();
                });
    }
}
