/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.spring.stereotype;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that informs Axon's auto configurer for Spring that a given {@link Component} is a saga instance.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
@Scope("prototype")
public @interface Saga {

    /**
     * Selects the name of the SagaStore bean. If left empty the saga will be stored in the Saga Store configured in the
     * global Axon Configuration.
     */
    String sagaStore() default "";

    /**
     * Defines the name of the bean that configures this Saga type. When defined, a bean of type {@link org.axonframework.config.SagaConfiguration} with such name must exist. If not defined, Axon will attempt to locate a bean named `&lt;sagaSimpleClassName&gt;Configuration`, creating a default configuration if none is found.
     */
    String configurationBean() default "";

}
