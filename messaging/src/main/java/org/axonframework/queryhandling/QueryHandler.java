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

/**
 * Interface describing a handler of {@link QueryMessage queries}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface QueryHandler extends MessageHandler {

    /**
     * Handles the given {@code query} within the given {@code context}.
     * <p>
     * The resulting {@link MessageStream stream} may contain zero, one, or N
     * {@link QueryResponseMessage response messages}.
     *
     * @param query   The query to handle.
     * @param context The context to the given {@code command} is handled in.
     * @return A {@code MessagesStream} of zero, one, or N {@link QueryResponseMessage response messages}.
     */
    @Nonnull
    MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                  @Nonnull ProcessingContext context);
}
