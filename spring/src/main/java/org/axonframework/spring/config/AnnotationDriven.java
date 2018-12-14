/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Annotation for {@link org.springframework.context.annotation.Configuration @Configuration} that will automatically
 * subscribe {@link CommandHandler @CommandHandler} and {@link QueryHandler @QueryHandler} annotated beans with the
 * CommandBus and QueryBus, respectively.
 *
 * @author Allard Buijze
 * @since 2.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AnnotationDrivenRegistrar.class)
public @interface AnnotationDriven {

}
