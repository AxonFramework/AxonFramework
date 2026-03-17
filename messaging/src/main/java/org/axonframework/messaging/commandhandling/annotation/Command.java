/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling.annotation;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.Message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark an object as a command.
 * <p>
 * Allows for specifying the business/domain {@link #name()} of the command, the {@link #version()} of the command, and
 * the command's {@link #routingKey() routing key}. The fields are used to map an annotated-command to a
 * {@link CommandMessage}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Message(messageType = CommandMessage.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Command {

    /**
     * The namespace or (bounded) context of the command.
     * <p>
     * Is used to define the {@link QualifiedName#namespace()} of a fully qualified name.
     * <p>
     * Defaults to the package name of the annotated class.
     *
     * @return The namespace or (bounded) context of the command.
     */
    String namespace() default "";

    /**
     * The business or domain name of the command.
     * <p>
     * Is used to define the {@link QualifiedName#localName()} of a fully qualified name.
     * <p>
     * Defaults to the simple name of the annotated class. Note that when a nested class is annotated, the simple name
     * does not include the names of the enclosing classes.
     *
     * @return The business or domain name of the command.
     */
    String name() default "";

    /**
     * The version of the command.
     * <p>
     * Will typically be mapped to the {@link MessageType#version()}. Defaults to {@link MessageType#DEFAULT_VERSION}.
     *
     * @return The version of the command.
     */
    String version() default MessageType.DEFAULT_VERSION;

    /**
     * The property of the command to be used as a routing key when dispatching the command.
     * <p>
     * The result of invoking the method referring to the given property will be set as the
     * {@link CommandMessage#routingKey()}.
     *
     * @return The property of the command to use as the routing key.
     */
    String routingKey() default "";
}
