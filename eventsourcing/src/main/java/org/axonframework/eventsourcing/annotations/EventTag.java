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

package org.axonframework.eventsourcing.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTag {

    /**
     * The key of the Tag which will be assigned to the Event. Defaults to annotated property name.
     *
     * @return The tag key
     */
    String key() default "";
}

/**
 * Get the name of the routing key property on commands and events that provides the identifier that should be used to
 * target the entity with the annotated member.
 * <p>
 * Optional. If left empty this defaults to the member name. If the member was named in a "getter" style, the
 * {@code "get"} will be removed.
 * <p>
 * Setting the {@code routingKey} is especially useful for annotated {@link java.lang.reflect.Method}s, which typically
 * have a different naming scheme than a field in a command/event.
 */