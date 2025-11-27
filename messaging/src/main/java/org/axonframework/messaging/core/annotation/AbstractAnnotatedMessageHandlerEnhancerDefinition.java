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

package org.axonframework.messaging.core.annotation;

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.core.MessageTypeResolver;

import java.util.Optional;

public abstract class AbstractAnnotatedMessageHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

    private final MessageTypeResolver messageTypeResolver = new AnnotationMessageTypeResolver();

    protected Optional<String> resolveMessageNameFromPayloadType(
            MessageHandlingMember<?> original,
            String attributeName,
            Class<? extends org.axonframework.messaging.core.Message> messageType
    ) {
        return original.<String>attribute(attributeName)
                       .filter(s -> !s.isEmpty())
                       .or(() -> AnnotationUtils
                               .<Class<? extends org.axonframework.messaging.core.Message>>findAnnotationAttribute(
                                       original.payloadType(),
                                       org.axonframework.messaging.core.annotation.Message.class,
                                       "messageType"
                               )
                               .filter(messageType::isAssignableFrom)
                               .map(mt -> messageTypeResolver
                                       .resolveOrThrow(original.payloadType())
                                       .qualifiedName()
                                       .toString()
                               ));
    }
}
