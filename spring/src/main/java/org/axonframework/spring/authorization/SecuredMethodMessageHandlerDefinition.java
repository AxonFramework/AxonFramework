/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.authorization;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * MessageHandlerDefinition that verifies authorization based on {@link org.springframework.security.access.annotation.Secured}
 * annotations on the message handler.
 *
 * @author Roald Bankras
 *
 * @since 4.10.0
 */
public class SecuredMethodMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.annotationAttributes(Secured.class)
                .map(attr -> (MessageHandlingMember<T>) new SecuredMessageHandlingMember<>(original, attr))
                .orElse(original);
    }

    private static class SecuredMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        private final Set<String> requiredRoles;

        public SecuredMessageHandlingMember(MessageHandlingMember<T> delegate,
                                            Map<String, Object> annotationAttributes) {
            super(delegate);
            requiredRoles = Set.of((String[]) annotationAttributes.get("secured"));
        }

        @Override
        public Object handle(@Nonnull Message<?> message, T target) throws Exception {
            if(!hasRequiredRoles(message)) {
                throw new UnauthorizedMessageException("Unauthorized message " + message.getIdentifier());
            }
            return super.handle(message, target);
        }

        private boolean hasRequiredRoles(@Nonnull Message<?> message) {
            Set<String> authorities = new HashSet<>();
            if (message.getMetaData().containsKey("authorities")) {
                ((Collection<? extends GrantedAuthority>) message.getMetaData().get("authorities"))
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .forEach(authorities::add);
            }
            authorities.retainAll(requiredRoles);
            return !authorities.isEmpty();
        }
    }
}
