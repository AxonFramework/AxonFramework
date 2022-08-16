/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.tracing.opentelemetry;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.axonframework.messaging.Message;

import javax.annotation.Nonnull;

/**
 * This {@link TextMapGetter} implementation is able to extract the parent OpenTelemetry span context from a
 * {@link Message}.
 * <p>
 * The trace parent is part of the message's {@link org.axonframework.messaging.MetaData}, if it was set when
 * dispatching by the {@link MetadataContextSetter}. This is done using the
 * {@link org.axonframework.tracing.SpanFactory#propagateContext(Message)} method for the message.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class MetadataContextGetter implements TextMapGetter<Message<?>> {

    /**
     * Singleton instance of the {@link MetadataContextGetter}, used by the {@link OpenTelemetrySpanFactory}.
     */
    public static final MetadataContextGetter INSTANCE = new MetadataContextGetter();

    private MetadataContextGetter() {
        // Should not be initialized directly, use the public static INSTANCE.
    }

    @Override
    public Iterable<String> keys(Message<?> message) {
        return message.getMetaData().keySet();
    }

    @Override
    public String get(Message<?> message, @Nonnull String key) {
        if (message == null) {
            return null;
        }
        return (String) message.getMetaData().get(key);
    }
}
