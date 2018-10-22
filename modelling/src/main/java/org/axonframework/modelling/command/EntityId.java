/*
 * Copyright (c) 2010-2018. Axon Framework
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
 * Field annotation that marks the field containing the identifier of an Entity. Commands for a child Entity are
 * routed to the Entity if the value of the Command's {@link #routingKey()} property matches the value of the annotated
 * field.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD})
public @interface EntityId {

    /**
     * Get the name of the routing key property on commands and events that provides the identifier that should be used
     * to target the entity with the annotated field.
     * <p>
     * Optional. If left empty this defaults to field name.
     */
    String routingKey() default "";

}
