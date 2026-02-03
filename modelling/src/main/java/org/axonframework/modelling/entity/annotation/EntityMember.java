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

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.modelling.entity.child.CommandTargetResolver;
import org.axonframework.modelling.entity.child.EventTargetMatcher;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on fields or methods of an entity that should be treated as a child entity.
 * <p>
 * Messages are filtered based on the {@link #eventTargetMatcher()} and {@link #commandTargetResolver()}, which both
 * default to a matcher based on the {@link #routingKey()}. The default routing key matcher and resolver will match a
 * property on the child entity and the message that matches the {@link #routingKey()} attribute.
 * <p>
 * The {@link #routingKey()} property is <b>required</b> whenever there are several child entities of the same type
 * within a class. Hence, it is required when a collection of child entities is used. However, it also holds when
 * <b>several</b> singular child entity fields of the same type exist within an entity. For purely singular child
 * entities (not backed by an {@link Iterable} nor reoccurring within the class), this can be omitted.
 * <p>
 * Note that this annotation existed as {@code org.axonframework.modelling.command.AggregateMember} before version
 * 5.0.0. Besides the change in name to align better with the entities instead of aggregates, the version introduced the
 * {@link #commandTargetResolver()}, which before was not configurable.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 3.0.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityMember {

    /**
     * The routing key to use for matching messages with this child entity.
     * <p>
     * This attribute is <b>required</b> whenever there are several child entities of the same type. For example, when a
     * collection of child entities is used.
     * <p>
     * Defaults to an empty string, which means no matching is taking place.
     *
     * @return the routing key to use for matching messages to this child entity
     */
    String routingKey() default "";

    /**
     * The {@link EventTargetMatcher} is used to determine which entity should be evolved by the {@link EventMessage}.
     * <p>
     * The forwarding mode can match 0, 1, or multiple child entities, and as such functions like a predicate or
     * filter.
     * <p>
     * Defaults to using the {@link RoutingKeyEventTargetMatcher}, which matches messages based on the
     * {@link #routingKey()}. Users can choose to create their own definition, and thus their own forwarding mode, that
     * has specific routing logic.
     *
     * @return the class defining the {@link EventTargetMatcher} for this child entity
     */
    // Used by AbstractEntityChildModelDefinition based on name
    @SuppressWarnings("unused")
    Class<? extends EventTargetMatcherDefinition> eventTargetMatcher()
            default RoutingKeyEventTargetMatcherDefinition.class;

    /**
     * The {@link CommandTargetResolverDefinition} is used to determine to which of the child entities a
     * {@link CommandMessage} should be forwarded.
     * <p>
     * The result should always be a single child entity, or no child entity at all, as command can only be handled by a
     * single entity.
     * <p>
     * The result can also be {@code null} to indicate that no child entity should handle the command. This allows users
     * to declare multiple fields with the same child entity type, but with different routing key properties, and thus
     * different routing logic for commands.
     * <p>
     * Defaults to using the {@link RoutingKeyCommandTargetResolverDefinition}, which matches messages based on the
     * {@link #routingKey()}. Users can choose to create their own definition, and thus their own forwarding mode, that
     * has specific routing logic.
     *
     * @return the class defining {@link CommandTargetResolver} for this child entity
     */
    // Used by AbstractEntityChildModelDefinition based on name
    @SuppressWarnings("unused")
    Class<? extends CommandTargetResolverDefinition> commandTargetResolver()
            default RoutingKeyCommandTargetResolverDefinition.class;
}
