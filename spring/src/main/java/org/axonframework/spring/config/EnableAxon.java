/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that enables Axon Configuration API for Spring. The Configuration is created and automatically configured
 * based on beans present in the Application Context.
 * <p>
 * Note that it is recommended to use the {@code axon-spring-boot-autoconfigure} module instead. Support for this
 * annotation is likely to be removed in future releases in favor of Spring's auto-configuration mechanism.
 * <p>
 * This annotation will also make a Bean of type {@link AxonConfiguration} available, which can be used for more
 * fine-grained configuration.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import(SpringAxonAutoConfigurer.ImportSelector.class)
@AnnotationDriven
public @interface EnableAxon {
}
