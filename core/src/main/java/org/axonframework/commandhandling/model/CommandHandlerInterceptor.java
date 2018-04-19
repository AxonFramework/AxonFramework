/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.annotation.MessageHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark methods on Aggregate members which can intercept commands. If a non-root entity of the
 * Aggregate is to intercept a command, the field declaring that entity must be annotated with {@link
 * org.axonframework.commandhandling.model.AggregateMember}. Command handler interceptor may intercept multiple
 * commands
 * depending on inheritance hierarchy of commands or on command name pattern provided.
 * <p>
 * It is possible to specify {@link org.axonframework.messaging.InterceptorChain} parameter as part of command handler
 * interceptor signature. If this parameter is not specified, command handler will be executed automatically.
 * Otherwise, implementor of command handler interceptor must invoke {@link org.axonframework.messaging.InterceptorChain#proceed()}
 * manually.
 * <p>
 * There are two ways to prevent command handler of specified command to be executed:
 * <ul>
 * <li>Throwing an exception</li>
 * <li>Specifying {@link org.axonframework.messaging.InterceptorChain} parameter and not calling {@link
 * org.axonframework.messaging.InterceptorChain#proceed()} method on it</li>
 * </ul>
 * <p>
 * It is possible to have multiple interceptors for the same command. In that case, if we have interceptor in parent
 * entity and child entity, parent one will be invoked first. Order of invoking interceptors within the same entity is
 * not specified.
 *
 * @author Milan Savic
 * @since 3.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@MessageHandler(messageType = CommandMessage.class)
public @interface CommandHandlerInterceptor {

    /**
     * Will filter commands which names match this pattern and invoke handler only with those commands.
     *
     * @return pattern used to filter command names
     */
    String commandNamePattern() default ".*";
}
