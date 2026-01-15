/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.child.EventTargetMatcher;

/**
 * {@link EventTargetMatcher} implementation that matches based on the routing key. If the routing key of the message
 * matches the routing key of the child entity, the child entity is considered a match.
 * <p>
 * Note: This class was known as {code org.axonframework.modelling.command.ForwardMatchingInstances} before version
 * 5.0.0.
 *
 * @param <E> The type of the child entity this matcher matches against.
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 3.1.0
 */
public class RoutingKeyEventTargetMatcher<E> implements EventTargetMatcher<E> {

    private final AnnotatedEntityModelRoutingKeyMatcher<E> routingKeyEntityMatcher;

    /**
     * Constructs a {@code RoutingKeyEventTargetMatcher} that matches the routing key of the given {@code entity}
     * against the routing key of an event message. The routing key of the entity is determined by the
     * {@code entityRoutingProperty} and the routing key of the event message is determined by the
     * {@code messageRoutingProperty}.
     *
     * @param metamodel              The {@link AnnotatedEntityMetamodel} of the entity to match against.
     * @param entityRoutingProperty  The routing key property of the entity, which is used to match against the
     *                               message.
     * @param messageRoutingProperty The routing key property of the message, which is used to match against the
     *                               entity.
     */
    public RoutingKeyEventTargetMatcher(@Nonnull AnnotatedEntityMetamodel<E> metamodel,
                                        @Nonnull String entityRoutingProperty,
                                        @Nonnull String messageRoutingProperty) {
        this.routingKeyEntityMatcher = new AnnotatedEntityModelRoutingKeyMatcher<>(
                metamodel,
                entityRoutingProperty,
                messageRoutingProperty);
    }


    @Override
    public boolean matches(@Nonnull E childEntity,
                           @Nonnull EventMessage message,
                           @Nonnull ProcessingContext processingContext) {
        return routingKeyEntityMatcher.matches(childEntity, message);
    }
}
