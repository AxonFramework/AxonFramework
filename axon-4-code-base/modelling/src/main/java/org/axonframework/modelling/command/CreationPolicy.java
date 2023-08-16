/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.annotation.HasHandlerAttributes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to specify the creation policy for a command handler. Default behavior is that command handlers
 * defined on a constructor would create a new instance of the aggregate, and command handlers defined on other methods
 * expect an existing aggregate. This annotation provides the option to define policy {@code
 * AggregateCreationPolicy.CREATE_IF_MISSING} or {@code AggregateCreationPolicy.ALWAYS} on a command handler to create a
 * new instance of the aggregate from a handler operation.
 *
 * @author Marc Gathier
 * @since 4.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@HasHandlerAttributes
public @interface CreationPolicy {

    /**
     * Specifies the {@link AggregateCreationPolicy} to apply. {@code NEVER} when not set.
     *
     * @return the creation policy
     */
    AggregateCreationPolicy value() default AggregateCreationPolicy.NEVER;
}
