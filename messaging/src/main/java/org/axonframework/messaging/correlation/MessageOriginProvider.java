/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * CorrelationDataProvider that provides the {@link Message#getIdentifier() identifier} of a {@link Message} to
 * other messages that are created as result of processing the first message.
 *
 * @author Rene de Waele
 */
public class MessageOriginProvider implements CorrelationDataProvider {
    private static final String DEFAULT_CORRELATION_KEY = "correlationId";
    private static final String DEFAULT_TRACE_KEY = "traceId";
    private static final String DEFAULT_TRACE_PARENT_KEY = "traceparent";

    /**
     * Returns the default metadata key for the correlation id of a message.
     *
     * @return the default metadata key for the correlation id
     */
    public static String getDefaultCorrelationKey() {
        return DEFAULT_CORRELATION_KEY;
    }

    /**
     * Returns the default metadata key for the trace id of a message.
     *
     * @return the default metadata key for the trace id
     */
    public static String getDefaultTraceKey() {
        return DEFAULT_TRACE_KEY;
    }


    private final String traceParentKey;

    private final String correlationKey;
    private final String traceKey;

    /**
     * Initializes a {@link MessageOriginProvider} that uses the default correlation id key:
     * {@link #getDefaultCorrelationKey()}, trace id key: {@link #getDefaultTraceKey()}, and trace parent key:
     * {@link #getDefaultTraceParentKey()}.
     */
    public MessageOriginProvider() {
        this(DEFAULT_CORRELATION_KEY, DEFAULT_TRACE_KEY, DEFAULT_TRACE_PARENT_KEY);
    }

    /**
     * Initializes a {@link MessageOriginProvider} that uses the given {@code correlationKey} and {@code traceKey}.
     *
     * @param correlationKey the key used to store the identifier of a message in the metadata of a resulting message
     * @param traceKey       the key used to store the identifier of the original message giving rise to the current
     *                       message
     */
    public MessageOriginProvider(String correlationKey, String traceKey) {
        this(correlationKey, traceKey, DEFAULT_TRACE_PARENT_KEY);
    }

    /**
     * Initializes a {@link MessageOriginProvider} that uses the given {@code correlationKey} and {@code traceKey}.
     *
     * @param correlationKey the key used to store the identifier of a message in the metadata of a resulting message
     * @param traceKey       the key used to store the identifier of the original message giving rise to the current
     *                       message
     * @param traceParentKey the key used to store the identifier of the trace parent to the current message
     */
    public MessageOriginProvider(String correlationKey, String traceKey, String traceParentKey) {
        this.correlationKey = correlationKey;
        this.traceKey = traceKey;
        this.traceParentKey = traceParentKey;
    }

    /**
     * Returns the default metadata key for the trace parent id of a message. This is based on the W3C standard for
     * tracing. See <a href="https://www.w3.org/TR/trace-context-1/">W3C Trace Context</a>.
     *
     * @return the default metadata key for the trace parent id
     */
    public static String getDefaultTraceParentKey() {
        return DEFAULT_TRACE_PARENT_KEY;
    }

    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        Map<String, Object> result = new HashMap<>();
        result.put(correlationKey, message.getIdentifier());
        result.put(traceKey, message.getMetaData().getOrDefault(traceKey, message.getIdentifier()));
        if (message.getMetaData().containsKey(traceParentKey)) {
            result.put(traceParentKey, message.getMetaData().get(traceParentKey));
        }
        return result;
    }
}
