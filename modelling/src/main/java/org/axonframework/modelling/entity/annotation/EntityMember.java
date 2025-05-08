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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on fields or methods of an entity that should be treated as a child entity. Messages can be
 * filtered based on the {@code eventForwardingMode} and {@code commandForwardingMode}, that both default to the
 * {@link RoutingKeyChildEntityMatcher}. This will match a property on the entity annotated with
 * {@link org.axonframework.commandhandling.annotation.RoutingKey} to a property of the message. The message property
 * defaults to the name of the property in the entity, but can be overriden through {@link #routingKey()}.
 * <p>
 * A {@link org.axonframework.commandhandling.annotation.RoutingKey} property is required in all collection child
 * entities when using the {@link RoutingKeyChildEntityMatcher} as the {@code eventForwardingMode} or
 * {@code commandForwardingMode}. For singular child entities, this can be ommitted as long as there is only one of that
 * type in the parent entity.
 * <p>
 * Note that this annotation existed as {@code org.axonframework.modelling.command.AggregateMember} before version
 * 5.0.0.
 *
 * @author Allard Buijze
 * @author Mitchell Herrijgers
 * @since 3.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityMember {

    String routingKey() default "";

    Class<? extends ChildEntityMatcherDefinition> eventForwardingMode() default RoutingKeyChildEntityMatcherDefinition.class;

    Class<? extends ChildEntityMatcherDefinition> commandForwardingMode() default RoutingKeyChildEntityMatcherDefinition.class;
}
