/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.spring.stereotype;

import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that informs Axon's auto configurer for Spring that a given {@link Component} is a saga instance.
 * <p>
 * Requires the {@code axon-legacy} module on the classpath, which carries the Saga itself. The Saga is registered on an
 * event processor named after the Saga type, {@code <SagaName>Processor}, unless a
 * {@link org.axonframework.messaging.core.annotation.Namespace @Namespace} on the Saga or an explicit
 * {@code EventProcessorDefinition} says otherwise. That processor is configured through the regular
 * {@code axon.eventhandling.processors.<SagaName>Processor} properties.
 * <p>
 * Like {@link EventSourced @EventSourced}, this stereotype lets Spring discover the type while Axon owns the lifecycle
 * of its instances. Spring collaborators are resolved as parameters of a
 * {@link SagaEventHandler @SagaEventHandler} method; Saga fields are not dependency-injected.
 * <p>
 * Sagas carry the Axon Framework 4 API, to ease migration of projects that cannot move off it in one go.
 *
 * @author Allard Buijze
 * @since 3.0
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
}
