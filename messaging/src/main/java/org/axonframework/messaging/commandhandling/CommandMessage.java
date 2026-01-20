/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.core.Message;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@link Message} carrying a command as its payload.
 * <p>
 * These messages carry an intention to change application state.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public interface CommandMessage extends Message {

    /**
     * Returns the routing key for this command message, if any is applicable.
     * <p>
     * Commands with the same routing key should be routed to the same handler if possible, as they are likely related
     * and might have to be executed in a specific order.
     *
     * @return The routing key for this command message, or an empty {@link Optional} if no routing key is set.
     **/
    Optional<String> routingKey();

    /**
     * Returns the priority of this {@link CommandMessage}, if any is applicable.
     * <p>
     * Commands with a higher priority should be handled before commands with a lower priority. Commands without a
     * priority are considered to have the lowest priority.
     *
     * @return The priority of this command message, or an empty {@link OptionalInt} if no priority is set.
     */
    OptionalInt priority();

    @Override
    @Nonnull
    CommandMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    CommandMessage andMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    default CommandMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default CommandMessage withConvertedPayload(@Nonnull TypeReference<?> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    CommandMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
