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

package org.axonframework.messaging.eventhandling.processing.streaming.token;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nonnull;
import java.util.Objects;

/**
 * A simple {@link ReplayContext} implementation that wraps a String value.
 * <p>
 * This is the recommended implementation for simple replay contexts where only
 * a descriptive string is needed to identify the reason for the replay.
 * <p>
 * Example usage:
 * <pre>{@code
 * ReplayToken.createReplayToken(tokenAtReset, startPosition, new StringReplayContext("data-migration-2024"));
 * // Or using the convenience factory method:
 * ReplayToken.createReplayToken(tokenAtReset, startPosition, "data-migration-2024");
 * }</pre>
 *
 * @param value the string value of this context
 * @author Axon Framework
 * @see ReplayContext
 * @see ReplayToken
 * @since 5.0.0
 */
public record StringReplayContext(
        @JsonProperty("value") @Nonnull String value
) implements ReplayContext {

    /**
     * Creates a new {@link StringReplayContext} with the given value.
     *
     * @param value the string value of this context, must not be {@code null}
     * @throws NullPointerException if value is {@code null}
     */
    @JsonCreator
    public StringReplayContext(@JsonProperty("value") @Nonnull String value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
        return value;
    }
}
