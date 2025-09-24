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

package org.axonframework.modelling.entity.annotations;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotations.RoutingKey;
import org.axonframework.modelling.entity.child.CommandTargetResolver;
import org.axonframework.modelling.entity.child.EventTargetMatcher;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on fields or methods of an entity that should be treated as a child entity. Messages are filtered
 * based on the {@link #eventTargetMatcher()} and {@link #commandTargetResolver()}, which both default to a matcher
 * based on the {@link #routingKey()}.
 * <p>
 * The default routing key matcher and resolver will match a property on the child entity annotated with
 * {@link RoutingKey} to a property of the message. The message property defaults to the name of the property in the
 * entity, but can be overridden through {@link #routingKey()}.
 * <p>
 * A {@link RoutingKey} property is required in all collection child entities when using the
 * {@link RoutingKeyEventTargetMatcher} as the {@code eventForwardingMode} or {@code commandForwardingMode}. For
 * singular child entities (not backed by an {@link Iterable}), this can be omitted.
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
     * The routing key to use for matching messages to this child entity. Defaults to an empty string, which means the
     * name of the {@link RoutingKey}-annotated property in the child entity will be used as the routing key.
     *
     * @return The routing key to use for matching messages to this child entity.
     */
    String routingKey() default "";

    /**
     * The {@link EventTargetMatcher} is used to determine which entity should be evolved by the
     * {@link org.axonframework.eventhandling.EventMessage}. The forwarding mode can match 0, 1, or multiple child
     * entities, and as such functions like a predicate or filter.
     * <p>
     * Defaults to using the {@link RoutingKeyEventTargetMatcher}, which matches messages based on the routing key.
     * Users can choose to create their own definition, and thus their own forwarding mode, that has specific routing
     * logic.
     *
     * @return The class defining the {@link EventTargetMatcher} for this child entity.
     */
    Class<? extends EventTargetMatcherDefinition> eventTargetMatcher()
            default RoutingKeyEventTargetMatcherDefinition.class;

    /**
     * The {@link CommandTargetResolverDefinition} is used to determine to which of the child entities a
     * {@link CommandMessage} should be forwarded. The result should always be a single child entity, or no child entity
     * at all, as command can only be handled by a single entity.
     * <p>
     * The result can also be {@code null} to indicate that no child entity should handle the command. This allows users
     * to declare multiple fields with the same child entity type, but with different {@link RoutingKey} properties, and
     * thus different routing logic for commands.
     * <p>
     * Defaults to using the {@link RoutingKeyCommandTargetResolverDefinition}, which matches messages based on the
     * routing key. Users can choose to create their own definition, and thus their own forwarding mode, that has
     * specific routing logic.
     *
     * @return The class defining {@link CommandTargetResolver} for this child entity.
     */
    Class<? extends CommandTargetResolverDefinition> commandTargetResolver()
            default RoutingKeyCommandTargetResolverDefinition.class;
}
