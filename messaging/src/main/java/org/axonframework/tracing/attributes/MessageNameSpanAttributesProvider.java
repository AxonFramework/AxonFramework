/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.tracing.attributes;

import org.axonframework.messaging.Message;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanAttributesProvider;

import java.util.Map;
import javax.annotation.Nonnull;

import static java.util.Collections.singletonMap;

/**
 * Adds the name of a {@link Message} to the {@link Span}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class MessageNameSpanAttributesProvider implements SpanAttributesProvider {

    @Override
    public @Nonnull Map<String, String> provideForMessage(@Nonnull Message<?> message) {
        return singletonMap("axon_message_name", message.type().toString());
    }
}
