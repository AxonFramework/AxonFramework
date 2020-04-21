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

package org.axonframework.commandhandling;

import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation to mark any method on an object as being a CommandHandler. Use the {@link
 * AnnotationCommandHandlerAdapter} to subscribe the annotated class to the command bus. This annotation can also be
 * placed directly on Aggregate members to have it handle the commands directly.
 * <p/>
 * The annotated method's first parameter is the command handled by that method. Optionally, the command handler may
 * specify a second parameter of type {@link UnitOfWork}. The active Unit of Work will be
 * passed if that parameter is supplied.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE})
@MessageHandler(messageType = CommandMessage.class)
public @interface CommandHandler {

    /**
     * The name of the Command this handler listens to. Defaults to the fully qualified class name of the payload type
     * (i.e. first parameter).
     *
     * @return The command name
     */
    String commandName() default "";

    /**
     * The property of the command to be used as a routing key towards this command handler instance. If multiple
     * handlers instances are available, a sending component is responsible to route commands with the same routing key
     * value to the correct instance.
     *
     * @return The property of the command to use as routing key
     */
    String routingKey() default "";

    /**
     * The type of payload expected by this handler. Defaults to the expected types expresses by (primarily the first)
     * parameters of the annotated Method or Constructor.
     *
     * @return the payload type expected by this handler
     */
    Class<?> payloadType() default Object.class;
}
