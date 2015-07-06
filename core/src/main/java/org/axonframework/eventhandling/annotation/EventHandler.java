/*
 * Copyright (c) 2010-2015. Axon Framework
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on methods that can handle events. The parameters of the annotated method are resolved using
 * parameter resolvers.
 * <p/>
 * Axon provides a number of parameter resolvers that allow you to use the following parameter types:<ul>
 * <li>The first parameter is always the payload of the Event message</li>
 * <li>Parameters annotated with <code>@MetaData</code> will resolve to the Meta Data value with the key as indicated
 * on the annotation. If required is false (default), null is passed when the meta data value is not present. If
 * required is true, the resolver will not match and prevent the method from being invoked when the meta data value is
 * not present.</li>
 * <li>Parameters of type {@link org.axonframework.domain.MetaData} will have the entire Meta Data of an Event Message
 * injected.</li>
 * <li>Parameters of type {@link org.joda.time.DateTime} (or any of its super classes or implemented interfaces)
 * annotated with {@link Timestamp @Timestamp} will resolve to the timestamp of the EventMessage. This is the time at
 * which the Event was generated.</li>
 * <li>Parameters assignable to {@link org.axonframework.domain.Message} will have the entire {@link
 * org.axonframework.domain.EventMessage} injected (if the message is assignable to that parameter). If the first
 * parameter is of type message, it effectively matches an Event of any type, even if generic parameters would suggest
 * otherwise. Due to type erasure, Axon cannot detect what parameter is expected. In such case, it is best to declare a
 * parameter of the payload type, followed by a parameter of type Message.</li>
 * <li>When using Spring and <code>&lt;axon:annotation-config/&gt;</code> is declared, any other parameters will
 * resolve to autowired beans, if exactly one autowire candidate is available in the application context. This allows
 * you to inject resources directly into <code>@EventHandler</code> annotated methods.</li>
 * </ul>
 * <p/>
 * For each event, only a single method will be invoked per object instance with annotated methods. This method is
 * resolved in the following order: <ol> <li>First, the event handler methods of the actual class (at runtime) are
 * searched <li>If a method is found with a parameter that the domain event can be assigned to, it is marked as
 * eligible
 * <li>After a class  has been evaluated (but before any super class), the most specific event handler method is
 * called.
 * That means that if an event handler for a class A and one for a class B are eligible, and B is a subclass of A, then
 * the method with a parameter of type B will be chosen<li>If no method is found in the actual class, its super class
 * is
 * evaluated. <li>If still no method is found, the event listener ignores the event </ol>
 * <p/>
 * If you do not want any events to be ignored, but rather have some logging of the fact that an unhandled event came
 * by, make an abstract superclass that contains an event handler method that accepts any <code>Object</code>.
 * <p/>
 * Note: if there are two event handler methods accepting the same argument, the behavior is undefined.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter
 * @see org.axonframework.common.annotation.ParameterResolverFactory
 * @since 0.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * The type of event this method handles. If specified, this handler will only be invoked for message that have a
     * payload assignable to the given payload type. If unspecified, the first parameter of the method defines the type
     * of supported event.
     */
    Class<?> eventType() default Void.class;
}
