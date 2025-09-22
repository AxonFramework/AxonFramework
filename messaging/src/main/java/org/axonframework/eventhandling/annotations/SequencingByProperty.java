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

package org.axonframework.eventhandling.annotations;

import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that provides a convenient way to apply {@link PropertySequencingPolicy} to event handlers.
 * This annotation simplifies the usage by allowing direct specification of the property name.
 * <p>
 * Instead of using:
 * <pre>{@code
 * @SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"orderId"})
 * }</pre>
 * <p>
 * You can use:
 * <pre>{@code
 * @SequencingByProperty("orderId")
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@SequencingPolicy(type = PropertySequencingPolicy.class)
public @interface SequencingByProperty {

    /**
     * The name of the property to be used for sequencing. This property will be extracted from the event payload
     * and used as the sequence identifier.
     *
     * @return The property name for sequencing.
     */
    String value();
}