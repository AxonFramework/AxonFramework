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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.child.EventTargetMatcher;

/**
 * {@link EventTargetMatcher} implementation that matches based on the routing key. If the routing key of the message
 * matches the routing key of the child entity, the child entity is considered a match.
 *
 * <p>
 * Note: This class was known as {code org.axonframework.modelling.command.ForwardMatchingInstances} before version
 * 5.0.0.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 3.1
 */
class RoutingKeyEventTargetMatcher<E> implements EventTargetMatcher<E> {

    private final AnnotatedEntityModelRoutingKeyMatcher<E> routingKeyEntityMatcher;

    public RoutingKeyEventTargetMatcher(AnnotatedEntityModel<E> entity,
                                        String entityRoutingProperty,
                                        String messageRoutingProperty) {
        this.routingKeyEntityMatcher = new AnnotatedEntityModelRoutingKeyMatcher<>(
                entity,
                entityRoutingProperty, messageRoutingProperty
        );
    }


    @Override
    public boolean matches(@Nonnull E childEntity,
                           @Nonnull EventMessage<?> message,
                           @Nonnull ProcessingContext processingContext) {
        return routingKeyEntityMatcher.matches(childEntity, message);
    }
}
