/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation to mark any method on an object as being a CommandHandler. Use the {@link
 * AnnotationCommandHandlerAdapter} to subscribe the annotated class to the command bus.
 * <p/>
 * Alternatively, the annotations may be placed on an AggregateRoot, in which case the {@link
 * AggregateAnnotationCommandHandler} can be used to subscribe the handlers to the command bus. When the annotation
 * appears on an Aggregate's constructor, that command will cause a new aggregate to be created and stored in the
 * repository provided with the {@link AggregateAnnotationCommandHandler}.
 * <p/>
 * The annotated method's first parameter is the command handled by that method. Optionally, the command handler may
 * specify a second parameter of type {@link org.axonframework.unitofwork.UnitOfWork}. The active Unit of Work will be
 * passed if that parameter is supplied.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface CommandHandler {

}
