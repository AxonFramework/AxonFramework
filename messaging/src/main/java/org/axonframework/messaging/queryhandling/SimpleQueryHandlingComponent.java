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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A simple implementation of the {@link QueryHandlingComponent} interface, allowing for easy registration of
 * {@link QueryHandler QueryHandlers} and other {@link QueryHandlingComponent QueryHandlingComponents}.
 * <p>
 * Registered subcomponents are preferred over registered query handlers when handling a query.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleQueryHandlingComponent implements
        QueryHandlingComponent,
        QueryHandlerRegistry<SimpleQueryHandlingComponent> {

    private final String name;
    private final Map<QualifiedName, QueryHandler> queryHandlers = new HashMap<>();
    private final Set<QueryHandlingComponent> subComponents = new HashSet<>();

    /**
     * Instantiates a simple {@link QueryHandlingComponent} that is able to handle query and delegate them to
     * subcomponents.
     *
     * @param name The name of the component, used for {@link DescribableComponent describing} the component.
     * @return A simple {@link QueryHandlingComponent} instance with the given {@code name}.
     */
    public static SimpleQueryHandlingComponent create(@Nonnull String name) {
        return new SimpleQueryHandlingComponent(name);
    }

    private SimpleQueryHandlingComponent(@Nonnull String name) {
        this.name = Assert.nonEmpty(name, "The name may not be null or empty.");
    }

    @Override
    public SimpleQueryHandlingComponent subscribe(@Nonnull QualifiedName queryName,
                                                  @Nonnull QueryHandler handler) {
        if (handler instanceof QueryHandlingComponent component) {
            return subscribe(component);
        }

        QueryHandler existingHandler = queryHandlers.computeIfAbsent(queryName, k -> handler);

        if (existingHandler != handler) {
            throw new DuplicateQueryHandlerSubscriptionException(queryName, existingHandler, handler);
        }

        return this;
    }

    @Override
    public SimpleQueryHandlingComponent subscribe(@Nonnull QueryHandlingComponent handlingComponent) {
        subComponents.add(handlingComponent);
        return this;
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage> handle(@Nonnull QueryMessage query,
                                                      @Nonnull ProcessingContext context) {
        QualifiedName handlerName = query.type().qualifiedName();
        Optional<QueryHandlingComponent> optionalSubHandler =
                subComponents.stream()
                             .filter(subComponent -> subComponent.supportedQueries().contains(handlerName))
                             .findFirst();
        if (optionalSubHandler.isPresent()) {
            try {
                return optionalSubHandler.get().handle(query, context);
            } catch (Throwable e) {
                return MessageStream.failed(e);
            }
        }
        if (queryHandlers.containsKey(handlerName)) {
            try {
                return queryHandlers.get(handlerName).handle(query, context);
            } catch (Throwable e) {
                return MessageStream.failed(e);
            }
        }
        return MessageStream.failed(NoHandlerForQueryException.forHandlingComponent(query));
    }

    @Override
    public Set<QualifiedName> supportedQueries() {
        Set<QualifiedName> combinedNames = new HashSet<>(queryHandlers.keySet());
        subComponents.forEach(subComponent -> combinedNames.addAll(subComponent.supportedQueries()));
        return combinedNames;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("queryHandlers", queryHandlers);
        descriptor.describeProperty("subComponents", subComponents);
    }
}
