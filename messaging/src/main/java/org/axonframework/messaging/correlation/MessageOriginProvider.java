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
 * A {@link CorrelationDataProvider} implementation that tracks message origin and causality by propagating correlation
 * metadata from one {@link Message} to subsequent {@code Messages} created during its processing.
 * <p>
 * This provider adds two key metadata entries to outgoing messages:
 * <ul>
 * <li><b>correlationId</b> - Identifies the overall business process or transaction (groups all related messages
 * together). This is the {@link Message#identifier() identifier} of the message that initiated the entire process. Once
 * set, it propagates unchanged through all subsequent messages, enabling tracing of the complete message chain back to
 * its origin.</li>
 * <li><b>causationId</b> - Identifies the direct parent message that caused the current message to be created (links to
 * the immediate trigger). This is always the {@link Message#identifier() identifier} of the message currently being
 * processed. For example, for an event, this would be the identifier of the command that triggered the event.</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create provider with default keys ("causationId" and "correlationId")
 * CorrelationDataProvider provider = new MessageOriginProvider();
 *
 * // Or customize the metadata keys
 * CorrelationDataProvider provider = new MessageOriginProvider("custom-causation-id", "custom-correlation-id");
 *
 * // Typically configured as part of the messaging infrastructure
 * MessagingConfigurer configurer = MessagingConfigurer.create()
 *     .correlationDataProviders(c -> List.of(new MessageOriginProvider()));
 * }</pre>
 *
 * @author Rene de Waele
 * @since 3.0.0
 */
public class MessageOriginProvider implements CorrelationDataProvider {

    /**
     * The default {@link Message#metadata() metadata} key used to store the causation identifier in a
     * {@link Message}.
     */
    public static final String DEFAULT_CAUSATION_KEY = "causationId";
    /**
     * The default {@link Message#metadata() metadata} key used to store the correlation identifier in a
     * {@link Message}.
     */
    public static final String DEFAULT_CORRELATION_KEY = "correlationId";

    private final String causationKey;
    private final String correlationKey;

    /**
     * Initializes a {@code MessageOriginProvider} using the {@link #DEFAULT_CAUSATION_KEY} ({@code "causationId"}) and
     * {@link #DEFAULT_CORRELATION_KEY} ({@code "correlationId"}) as the metadata keys.
     */
    public MessageOriginProvider() {
        this(DEFAULT_CAUSATION_KEY, DEFAULT_CORRELATION_KEY);
    }

    /**
     * Initializes a {@code MessageOriginProvider} with custom metadata keys for causation and correlation tracking.
     *
     * @param causationKey   The {@link Message#metadata() metadata} key used to store the causation identifier (the
     *                       {@link Message#identifier() identifier} of the message that directly caused the current
     *                       message).
     * @param correlationKey The {@link Message#metadata() metadata} key used to store the correlation identifier (the
     *                       {@link Message#identifier() identifier} of the message that initiated the overall business
     *                       process).
     */
    public MessageOriginProvider(@Nonnull String causationKey,
                                 @Nonnull String correlationKey) {
        this.causationKey = Objects.requireNonNull(causationKey, "Causation key must not be null.");
        this.correlationKey = Objects.requireNonNull(correlationKey, "Correlation key must not be null.");
    }

    /**
     * Extracts correlation data from the given {@link Message} to be propagated to subsequent messages.
     * <p>
     * This method returns a map containing two entries:
     * <ul>
     * <li><b>causationKey</b> - Set to the {@link Message#identifier() identifier} of the current message, establishing
     * it as the direct cause of any subsequent messages.</li>
     * <li><b>correlationKey</b> - Set to the correlation identifier from the current message's
     * {@link Message#metadata() metadata}, or to the current message's {@link Message#identifier() identifier} if no
     * correlation identifier exists (i.e., this is the first message in the chain).</li>
     * </ul>
     *
     * @param message The {@link Message} being processed from which to extract correlation data.
     * @return A {@link Map} containing the causation and correlation identifiers to be added to subsequent messages.
     */
    @Nonnull
    @Override
    public Map<String, String> correlationDataFor(@Nonnull Message message) {
        Map<String, String> result = new HashMap<>();
        result.put(causationKey, message.identifier());
        result.put(correlationKey, message.metadata().getOrDefault(correlationKey, message.identifier()));
        return result;
    }
}
