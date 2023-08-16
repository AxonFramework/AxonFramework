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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for {@link org.springframework.context.annotation.Configuration @Configuration} that will automatically
 * subscribe {@link CommandHandler @CommandHandler} and {@link QueryHandler @QueryHandler} annotated beans with the
 * CommandBus and QueryBus, respectively.
 *
 * @author Allard Buijze
 * @see MessageHandlerLookup
 * @see SpringSagaLookup
 * @see SpringAggregateLookup
 * @see SpringConfigurer
 * @see SpringAxonConfiguration
 * @since 2.3
 * @deprecated Use Spring Boot autoconfiguration or register the individual beans explicitly. Check the "See also" list
 * for which individual beans to register.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AnnotationDrivenRegistrar.class)
@Deprecated
public @interface AnnotationDriven {

}
