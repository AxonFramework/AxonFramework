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
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.child.ChildAmbiguityException;
import org.axonframework.modelling.entity.child.CommandTargetResolver;
import org.axonframework.modelling.entity.child.EventTargetMatcher;

import java.util.List;

/**
 * {@link EventTargetMatcher} implementation that matches based on the routing key. If the routing key of the message
 * matches the routing key of the child entity, the child entity is considered a match.
 * <p>
 * Note: This class was known as {code org.axonframework.modelling.command.ForwardMatchingInstances} before version
 * 5.0.0.
 *
 * @param <E> The type of the child entity this resolver matches against.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class RoutingKeyCommandTargetResolver<E> implements CommandTargetResolver<E> {

    private final AnnotatedEntityModelRoutingKeyMatcher<E> routingKeyEntityMatcher;

    /**
     * Constructs a {@code RoutingKeyCommandTargetResolver} that matches the routing key of the given {@code entity}
     * against the routing key of a command message. The routing key of the entity is determined by the
     * {@code entityRoutingProperty} and the routing key of the command message is determined by the
     * {@code messageRoutingProperty}.
     *
     * @param metamodel              The {@link AnnotatedEntityMetamodel} of the entity to match against.
     * @param entityRoutingProperty  The routing key property of the entity, which is used to match against the
     *                               command.
     * @param messageRoutingProperty The routing key property of the command, which is used to match against the
     *                               entity.
     */
    public RoutingKeyCommandTargetResolver(@Nonnull AnnotatedEntityMetamodel<E> metamodel,
                                           @Nonnull String entityRoutingProperty,
                                           @Nonnull String messageRoutingProperty) {
        this.routingKeyEntityMatcher = new AnnotatedEntityModelRoutingKeyMatcher<>(
                metamodel,
                entityRoutingProperty,
                messageRoutingProperty
        );
    }


    @Override
    public E getTargetChildEntity(@Nonnull List<E> childEntities,
                                  @Nonnull CommandMessage message,
                                  @Nonnull ProcessingContext context) {
        List<E> matchingCandidates = childEntities.stream()
                                                  .filter(entity -> routingKeyEntityMatcher.matches(entity, message))
                                                  .toList();
        if (matchingCandidates.size() > 1) {
            throw new ChildAmbiguityException(
                    "Multiple child entities found for command of type [%s], while only one was expected. Matching candidates are: [%s]".formatted(
                            message.type(),
                            matchingCandidates
                    ));
        }
        if (matchingCandidates.size() == 1) {
            return matchingCandidates.getFirst();
        }
        return null;
    }
}
