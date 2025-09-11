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

package org.axonframework.messaging.correlation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@code CorrelationDataProvider} implementation that provides the {@link Message#identifier() identifier} of a
 * {@link Message} to other {@code Messages} that are created as result of processing the given {@code message}.
 *
 * @author Rene de Waele
 * @since 3.0.0
 */
public class MessageOriginProvider implements CorrelationDataProvider {

    /**
     * The default {@link Message#metaData() metadata} key for the correlation identifier of a {@link Message}.
     */
    public static final String DEFAULT_CORRELATION_KEY = "correlationId";
    /**
     * The default {@link Message#metaData() metadata} key for the trace identifier of a {@link Message}.
     */
    public static final String DEFAULT_TRACE_KEY = "traceId";

    private final String correlationKey;
    private final String traceKey;

    /**
     * Initializes a {@code MessageOriginProvider} using the {@link #DEFAULT_CORRELATION_KEY} and
     * {@link #DEFAULT_TRACE_KEY} as the {@code correlationKey} and {@code traceKey} respectively.
     */
    public MessageOriginProvider() {
        this(DEFAULT_CORRELATION_KEY, DEFAULT_TRACE_KEY);
    }

    /**
     * Initializes a {@code MessageOriginProvider} that uses the given {@code correlationKey}.
     *
     * @param correlationKey The key used to store the identifier of a {@link Message} in the {@link Message#metaData()}
     *                       of a resulting {@code Message}.
     * @param traceKey       The key used to store the identifier of the original {@link Message} giving rise to the
     *                       current {@code Message}.
     */
    public MessageOriginProvider(@Nonnull String correlationKey,
                                 @Nonnull String traceKey) {
        this.correlationKey = Objects.requireNonNull(correlationKey, "Correlation key must not be null.");
        this.traceKey = Objects.requireNonNull(traceKey, "Trace key must not be null.");
    }

    @Nonnull
    @Override
    public Map<String, String> correlationDataFor(@Nonnull Message message) {
        Map<String, String> result = new HashMap<>();
        result.put(correlationKey, message.identifier());
        result.put(traceKey, message.metadata().getOrDefault(traceKey, message.identifier()));
        return result;
    }
}
