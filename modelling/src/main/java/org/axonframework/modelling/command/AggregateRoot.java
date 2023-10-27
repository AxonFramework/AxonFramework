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

import java.lang.annotation.*;

/**
 * Annotation placed on types that should be treated as the root of an aggregate. Such types will be the entry point for
 * command messages that target the aggregate.
 * <p>
 * The use of this annotation is not mandatory for the framework. It allows setting an explicit type name for the
 * Aggregate. Other than that, it's probably useful for developers.
 */
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AggregateRoot {
    /**
     * Get the String representation of the aggregate's type. Optional. This defaults to the simple name of the
     * annotated class.
     */
    String type() default "";
}
