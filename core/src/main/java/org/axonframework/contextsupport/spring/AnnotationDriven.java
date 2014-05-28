/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for {@link org.springframework.context.annotation.Configuration @Configuration} that will automatically
 * subscribe {@link org.axonframework.commandhandling.annotation.CommandHandler @CommandHandler} and {@link
 * org.axonframework.eventhandling.annotation.EventHandler @EventHandler} annotated beans with the CommandBus and
 * EventBus, respectively.
 * <p/>
 * If a context contains multiple EventBus or CommandBus implementations, you must indicate the isntance to use as
 * a property on this annotation.
 *
 * @author Allard Buijze
 * @since 2.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AnnotationDrivenConfiguration.class)
public @interface AnnotationDriven {

    /**
     * The bean name of the Event Bus to register {@link
     * org.axonframework.eventhandling.annotation.EventHandler @EventHandler} annotated beans with.
     */
    String eventBus() default "";

    /**
     * The bean name of the Command Bus to register {@link
     * org.axonframework.commandhandling.annotation.CommandHandler @CommandHandler} annotated beans with.
     */
    String commandBus() default "";

    /**
     * Whether to unsubscribe beans on shutdown. Default to <code>false</code>. Setting this to <code>true</code> will
     * explicitly unsubscribe beans from the Event- and CommandBus when shutting down the application context.
     */
    boolean unsubscribeOnShutdown() default false;

    /**
     * The phase in the application context lifecycle in which to subscribe (and possibly unsubscribe) the handlers
     * with the CommandBus and EventBus.
     */
    int phase() default 0;
}
