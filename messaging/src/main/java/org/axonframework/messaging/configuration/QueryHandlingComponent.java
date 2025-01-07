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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QueryHandlingComponent implements MessageHandlingComponent<QueryMessage<?, ?>, QueryResponseMessage<?>> {

    private final ConcurrentHashMap<QualifiedName, QueryHandler> queryHandlers;

    public QueryHandlingComponent() {
        this.queryHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                         @Nonnull ProcessingContext context) {
        QualifiedName name = query.name();
        // TODO add interceptor knowledge
        QueryHandler handler = queryHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for query with name [" + name + "]"
            ));
        }
        // TODO - can we do something about this cast?
        return (MessageStream<QueryResponseMessage<?>>) handler.apply(query, context);
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> QueryHandlingComponent subscribe(
            @Nonnull Set<QualifiedName> names,
            @Nonnull H messageHandler
    ) {
        if (messageHandler instanceof CommandHandler) {
            throw new UnsupportedOperationException("Cannot register command handlers on a query handling component");
        }
        if (messageHandler instanceof EventHandler) {
            throw new UnsupportedOperationException("Cannot register event handlers on a query handling component");
        }
        names.forEach(name -> queryHandlers.put(name, (QueryHandler) messageHandler));
        return this;
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> QueryHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return subscribe(Set.of(name), messageHandler);
    }

    /**
     * @param names
     * @param queryHandler
     * @param <Q>
     * @return
     */
    public <Q extends QueryHandler> QueryHandlingComponent registerQueryHandler(@Nonnull Set<QualifiedName> names,
                                                                                @Nonnull Q queryHandler) {
        return subscribe(names, queryHandler);
    }

    /**
     * @param name
     * @param queryHandler
     * @param <Q>
     * @return
     */
    public <Q extends QueryHandler> QueryHandlingComponent registerQueryHandler(@Nonnull QualifiedName name,
                                                                                @Nonnull Q queryHandler) {
        return registerQueryHandler(Set.of(name), queryHandler);
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        return queryHandlers.keySet();
    }
}
