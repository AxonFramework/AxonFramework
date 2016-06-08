/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;

import java.lang.annotation.*;

/**
 * Method-level annotation indicating that the annotated method i an event handler method for the saga instance.
 * <p/>
 * Annotated methods must comply to a few simple rules: <ul> <li>The method must accept 1 parameter: a subtype of
 * {@link
 * EventMessage} <li>Return values are allowed, but are ignored<li>Exceptions are highly
 * discouraged,
 * and are likely to be caught and ignored by the dispatchers </ul>
 * <p/>
 * For each event, only a single annotated method will be invoked. This method is resolved in the following order <ol>
 * <li>First, the event handler methods of the actual class (at runtime) are searched <li>If a method is found with a
 * parameter that the domain event can be assigned to, it is marked as eligible <li>After a class  has been evaluated
 * (but before any super class), the most specific event handler method is called. That means that if an event handler
 * for a class A and one for a class B are eligible, and B is a subclass of A, then the method with a parameter of type
 * B will be chosen<li>If no method is found in the actual class, its super class is evaluated. <li>If still no method
 * is found, the event listener ignores the event </ol>
 *
 * @author Allard Buijze
 * @see EventHandler
 * @since 0.7
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@EventHandler
public @interface SagaEventHandler {

    /**
     * The property in the event that will provide the value to find the Saga instance. Typically, this value is an
     * aggregate identifier of an aggregate that a specific saga monitors.
     */
    String associationProperty();

    /**
     * The key in the AssociationValue to use. Optional. Should only be configured if that property is different than
     * the value given by {@link #associationProperty()}.
     */
    String keyName() default "";

    /**
     * The type of event this method handles. If specified, this handler will only be invoked for message that have a
     * payload assignable to the given payload type. If unspecified, the first parameter of the method defines the type
     * of supported event.
     */
    Class<?> payloadType() default Void.class;
}
