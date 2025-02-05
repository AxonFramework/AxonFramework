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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleQueryHandlingComponent implements QueryHandlingComponent {

    private final ConcurrentHashMap<QualifiedName, QueryHandler> queryHandlers;

    public SimpleQueryHandlingComponent() {
        this.queryHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                         @Nonnull ProcessingContext context) {
        QualifiedName name = query.type().qualifiedName();
        // TODO add interceptor knowledge
        QueryHandler handler = queryHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for query with name [" + name + "]"
            ));
        }
        return handler.handle(query, context);
    }

    @Override
    public SimpleQueryHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                                  @Nonnull QueryHandler handler) {
        names.forEach(name -> queryHandlers.put(name, Objects.requireNonNull(handler, "TODO")));
        return this;
    }

    @Override
    public SimpleQueryHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                  @Nonnull QueryHandler handler) {
        return subscribe(Set.of(name), handler);
    }

    @Override
    public Set<QualifiedName> supportedQueries() {
        return Set.copyOf(queryHandlers.keySet());
    }
}
