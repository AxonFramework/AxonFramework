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

package org.axonframework.modelling.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link java.lang.reflect.Field} or {@link java.lang.reflect.Method} annotation that marks the member containing the
 * identifier of an Entity. Commands for a child Entity are routed to the Entity if the value of the Command's {@link
 * #routingKey()} property matches the value of the annotated field.
 *
 * @author Allard Buijze
 * @since 3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface EntityId {

    /**
     * Get the name of the routing key property on commands and events that provides the identifier that should be used
     * to target the entity with the annotated member.
     * <p>
     * Optional. If left empty this defaults to the member name. If the member was named in a "getter" style, the {@code
     * "get"} will be removed.
     * <p>
     * Setting the {@code routingKey} is especially useful for annotated {@link java.lang.reflect.Method}s, which
     * typically have a different naming scheme than a field in a command/event.
     */
    String routingKey() default "";
}
