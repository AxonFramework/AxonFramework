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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryHandlingComponent;

import java.util.Set;

/**
 * TODO This should be regarded as a playground object to verify the API.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class QueryModelComponent implements EventHandlingComponent, QueryHandlingComponent {

    private final SimpleEventHandlingComponent eventComponent;
    private final SimpleQueryHandlingComponent queryComponent;

    public QueryModelComponent() {
        this.eventComponent = new SimpleEventHandlingComponent();
        this.queryComponent = new SimpleQueryHandlingComponent();
    }

    @Override
    public QueryModelComponent subscribe(@Nonnull QualifiedName name,
                                         @Nonnull QueryHandler queryHandler) {
        queryComponent.subscribe(name, queryHandler);
        return this;
    }

    @Override
    public QueryModelComponent subscribe(@Nonnull QualifiedName name,
                                         @Nonnull EventHandler eventHandler) {
        eventComponent.subscribe(name, eventHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedQueries() {
        return queryComponent.supportedQueries();
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return eventComponent.supportedEvents();
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                         @Nonnull ProcessingContext context) {
        return queryComponent.handle(query, context);
    }

    @Nonnull
    @Override
    public MessageStream<NoMessage> handle(@Nonnull EventMessage<?> event,
                                           @Nonnull ProcessingContext context) {
        return eventComponent.handle(event, context);
    }
}
