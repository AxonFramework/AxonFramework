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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark methods on Aggregate members which can intercept commands. If a non-root entity of the
 * Aggregate is to intercept a command, the field declaring that entity must be annotated with {@link
 * AggregateMember}. Command handler interceptor may intercept multiple
 * commands depending on inheritance hierarchy of commands or on command name pattern provided. Any incoming command
 * that matches the signature of the annotated method and the {@link #commandNamePattern()} will be intercepted by
 * the annotated method.
 * <p>
 * It is possible to specify {@link org.axonframework.messaging.InterceptorChain} parameter as part of command handler
 * interceptor signature. If this parameter is not specified, command handler will be executed automatically, as if
 * the {@link InterceptorChain#proceedSync()} was invoked as the last instruction.
 * <p>
 * If a parameter of type {@link InterceptorChain} is defined, it must be called to have the command handler invoked.
 * It may choose to return the result of the {@link InterceptorChain#proceedSync()} call directly, change it, or even
 * discard it.
 * <p>
 * Annotated methods that do not declare an {@link InterceptorChain} parameter must declare a {@code void} return type,
 * as they cannot alter the result of an invocation, other than by throwing an exception.
 * <p>
 * There are two ways to prevent command handler of specified command to be executed:
 * <ul>
 * <li>Throwing an exception</li>
 * <li>Specifying {@link org.axonframework.messaging.InterceptorChain} parameter and not calling {@link
 * org.axonframework.messaging.InterceptorChain#proceedSync()} method on it</li>
 * </ul>
 * <p>
 * It is possible to have multiple interceptors for the same command. In that case, if there are interceptors in both
 * parent and child entity, the method in the parent entity will be invoked first. The order of invocation of
 * interceptors within the same entity is not specified.
 *
 * @author Milan Savic
 * @since 3.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@MessageHandlerInterceptor(messageType = CommandMessage.class)
public @interface CommandHandlerInterceptor {

    /**
     * Will filter commands which names match this pattern and invoke handler only with those commands.
     *
     * @return pattern used to filter command names
     */
    String commandNamePattern() default ".*";

    /**
     * Specifies the type of message payload that can be handled by the member method. The payload of the message should
     * be assignable to this type. Defaults to any {@link Object}.
     */
    Class<?> payloadType() default Object.class;
}
