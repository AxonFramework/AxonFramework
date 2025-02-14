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
 * Field or method level annotation that marks a field or method providing the Tag for the Event. The member name will
 * be used as the {@link org.axonframework.eventsourcing.eventstore.Tag#key}. The member value will be used as the
 * {@link org.axonframework.eventsourcing.eventstore.Tag#value}.
 * <p>
 * For both fields and methods, the value is obtained by calling {@code toString()} on the field value or method return
 * value. If the value is null, no tag will be created.
 * <p>
 * If placed on a method, that method must contain no parameters and returns non-void value.
 * <p>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTag {

    /**
     * The key of the Tag which will be assigned to the Event. Optional. If left empty this defaults to the member name.
     * If the member was named in a "getter" style, the {@code "get"} will be removed.
     *
     * @return The tag key
     */
    String key() default "";
}