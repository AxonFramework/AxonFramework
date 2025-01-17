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
import org.axonframework.messaging.configuration.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.stream.Stream;

/**
 * TODO documentation
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public /*non-sealed */interface QueryHandler extends MessageHandler {

    /**
     * @param query
     * @param context
     * @return
     */
    @Nonnull
    MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                  @Nonnull ProcessingContext context);

    // TODO discuss if we want to deviate from the MessageStream here.
    // Foreseen downside of doing so, is removal of void/CompletableFuture/Mono return type flexibility
    default Stream<? extends QueryResponseMessage<?>> handleSimple(@Nonnull QueryMessage<?, ?> query,
                                                                   @Nonnull ProcessingContext context) {
        return handle(query, context).asFlux().map(MessageStream.Entry::message).toStream();
    }
}
