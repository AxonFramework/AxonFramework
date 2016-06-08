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

package org.axonframework.commandhandling;

import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.lang.annotation.*;

/**
 * Marker annotation to mark any method on an object as being a CommandHandler. Use the {@link
 * AnnotationCommandHandlerAdapter} to subscribe the annotated class to the command bus.
 * <p/>
 * Alternatively, the annotations may be placed on an Aggregate members, in which case the {@link
 * AggregateAnnotationCommandHandler} can be used to subscribe the handlers to the command bus. When the annotation
 * appears on an Aggregate root's constructor, that command will cause a new aggregate to be created and stored in the
 * repository provided with the {@link AggregateAnnotationCommandHandler}. If a non-root entity of the Aggregate is
 * to handle a command, the field declaring that entity must be annotated with {@link
 * CommandHandlingMember}. Note that the annotation may not be placed on
 * a non-root Entity's constructor.
 * <p/>
 * The annotated method's first parameter is the command handled by that method. Optionally, the command handler may
 * specify a second parameter of type {@link UnitOfWork}. The active Unit of Work will be
 * passed if that parameter is supplied.
 *
 * @author Allard Buijze
 * @see CommandHandlingMember
 * @since 0.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE})
public @interface CommandHandler {

    /**
     * The name of the Command this handler listens to. Defaults to the fully qualified class name of the payload type
     * (i.e. first parameter).
     */
    String commandName() default "";

    /**
     * The property of the command to be used as a routing key towards this command handler instance. If multiple
     * handlers instances are available, a sending component is responsible to route commands with the same routing key
     * value to the correct instance.
     */
    String routingKey() default "";
}
