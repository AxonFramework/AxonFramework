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

import io.opentelemetry.context.propagation.TextMapSetter;
import org.axonframework.messaging.Message;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * This {@link TextMapSetter} implementation is able to insert the current OpenTelemetry span context into a
 * {@link Message}. However, since a {@code Message} is immutable, this injector injects it into the provided
 * {@link Map}. It's the responsibility the implementing {@link OpenTelemetrySpanFactory} to mutate the message through
 * {@link OpenTelemetrySpanFactory#propagateContext(Message)}.
 * <p>
 * The trace becomes the message's parent span in its{@link org.axonframework.messaging.MetaData}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class MetadataContextSetter implements TextMapSetter<Map<String, String>> {

    /**
     * Singleton instance of the {@link MetadataContextSetter}, used by the {@link OpenTelemetrySpanFactory}.
     */
    public static final MetadataContextSetter INSTANCE = new MetadataContextSetter();

    private MetadataContextSetter() {
        // Should not be initialized directly, use the public static INSTANCE.
    }

    @Override
    public void set(Map<String, String> metadata, @Nonnull String key, @Nonnull String value) {
        if (metadata == null) {
            throw new IllegalArgumentException("The provided metadata may not be null!");
        }
        metadata.put(key, value);
    }
}
