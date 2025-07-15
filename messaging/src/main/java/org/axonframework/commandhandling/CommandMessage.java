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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link Message} carrying a command as its payload.
 * <p>
 * These messages carry an intention to change application state.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link CommandMessage}.
 * @author Allard Buijze
 * @since 2.0.0
 */
public interface CommandMessage<P> extends Message<P> {

    /**
     * Returns the routing key for this command message, if any is applicable. Commands with the same routing key should
     * be routed to the same handler if possible, as they are likely related and might have to be executed in a specific
     * order.
     *
     * @return The routing key for this command message, or an empty {@link Optional} if no routing key is set.
     **/
    Optional<String> routingKey();

    /**
     * Returns the priority of this command message, if any is applicable. Commands with a higher priority should be
     * handled before commands with a lower priority. Commands without a priority are considered to have to lowest
     * priority.
     *
     * @return The priority of this command message, or an empty {@link Optional} if no priority is set.
     */
    Optional<Long> priority();

    @Override
    CommandMessage<P> withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    CommandMessage<P> andMetaData(@Nonnull Map<String, String> metaData);

    @Override
    <C> CommandMessage<C> withConvertedPayload(@Nonnull Function<P, C> conversion);
}
