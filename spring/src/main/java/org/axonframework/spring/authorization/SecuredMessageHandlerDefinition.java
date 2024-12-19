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

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * MessageHandlerDefinition that verifies authorization based on
 * {@link org.springframework.security.access.annotation.Secured} annotations on the message handler.
 *
 * @author Roald Bankras
 * @since 4.11.0
 */
public class SecuredMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.annotationAttributes(Secured.class)
                       .filter(attributes -> attributes.containsKey("secured"))
                       .map(attributes -> (String[]) attributes.get("secured"))
                       .map(securityConfiguration -> (MessageHandlingMember<T>) new SecuredMessageHandlingMember<>(
                               original, securityConfiguration
                       ))
                       .orElse(original);
    }

    private static class SecuredMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        private final Set<String> requiredRoles;

        public SecuredMessageHandlingMember(MessageHandlingMember<T> delegate, String[] secureds) {
            super(delegate);
            requiredRoles = new HashSet<>(Arrays.asList(secureds));
        }

        @Override
        public Object handle(@Nonnull Message<?> message, T target) throws Exception {
            if (!hasRequiredRoles(message)) {
                throw new UnauthorizedMessageException(
                        "Unauthorized message with identifier [" + message.getIdentifier() + "]"
                );
            }
            return super.handle(message, target);
        }

        private boolean hasRequiredRoles(@Nonnull Message<?> message) {
            Set<String> authorities = new HashSet<>();
            if (message.getMetaData().containsKey("authorities")) {
                //noinspection unchecked
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
