/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.annotation;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.SequencingPolicy;
import org.axonframework.eventhandling.SequentialPolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p/>
 * Annotation that marks a class as an Asynchronous EventListener. Postprocessors will detect this bean and wrap it
 * with
 * a suitable AsynchronousEventHandlerWrapper. This type-level annotation allows the definition of the concurrency
 * policy for this EventListener.
 * <p/>
 * This annotation allows the configuration of any {@link #sequencingPolicyClass() arbitrary class}, as long as it
 * implements the {@link org.axonframework.eventhandling.SequencingPolicy} interface. It also needs to have (at least)
 * a
 * no-arg constructor.
 *
 * @author Allard Buijze
 * @since 0.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AsynchronousEventListener {

    /**
     * Defines the policy type to use for event handling sequencing. The provided class must implement {@link
     * org.axonframework.eventhandling.SequencingPolicy} and provide an accessible no-arg constructor.
     */
    Class<? extends SequencingPolicy<? super EventMessage>> sequencingPolicyClass() default SequentialPolicy.class;
}
