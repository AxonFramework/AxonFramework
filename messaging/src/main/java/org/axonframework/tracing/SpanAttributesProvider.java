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

package org.axonframework.tracing;

import org.axonframework.messaging.Message;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Represents a provider of attributes to a {@link Span}, based on a {@link Message}. It's the responsibility of the
 * {@link SpanFactory} to invoke these and add the attributes to the {@link Span}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public interface SpanAttributesProvider {

    /**
     * Provides a map of attributes to add to the {@link Span} based on the {@link Message} provided.
     *
     * @param message The message
     * @return The attributes
     */
    @Nonnull
    Map<String, String> provideForMessage(@Nonnull Message<?> message);
}
