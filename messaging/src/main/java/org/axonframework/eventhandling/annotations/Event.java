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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.Message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark an object as an event.
 * <p>
 * Allows for specifying the business/domain {@link #name()} of the event and the {@link #version()} of the event. The
 * fields are used to map an annotated-event to a {@link org.axonframework.eventhandling.EventMessage}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Message(messageType = EventMessage.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Event {

    /**
     * The namespace or (bounded) context of the event.
     * <p>
     * Will typically be mapped to the {@link QualifiedName#namespace()}. Whenever this attribute is defined, the
     * {@link #name()} will become the {@link QualifiedName#localName()}. Together they would form the
     * {@link QualifiedName#name()}.
     *
     * @return The namespace or (bounded) context of the event.
     */
    String namespace() default "";

    /**
     * The business or domain name of the event.
     * <p>
     * Will typically be mapped to a {@link QualifiedName#QualifiedName(String)} and inserted into a
     * {@link MessageType}. By using the String-based constructor of the {@link QualifiedName}, this field  will
     * represent the combination of the {@link QualifiedName#localName()} and {@link QualifiedName#namespace()},
     *
     * @return The business or domain name of the event.
     */
    String name() default "";

    /**
     * The version of the event.
     * <p>
     * Will typically be mapped to the {@link MessageType#version()}. Defaults to {@link MessageType#DEFAULT_VERSION}.
     *
     * @return The version of the event.
     */
    String version() default MessageType.DEFAULT_VERSION;
}
