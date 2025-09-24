/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.annotations;

import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.modelling.annotations.TargetEntityId;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a method can be used to resolve the {@link EventCriteria} based on the
 * {@link TargetEntityId} when loading an {@link EventSourcedEntity}.
 * <p>
 * The method should be a static method that returns an {@link EventCriteria} instance. The first argument should be the
 * identifier of the entity to load. If you need to resolve multiple identifier types, you can use the
 * {@link EventCriteriaBuilder} annotation on multiple methods.
 * <p>
 * You can define any component from the {@link org.axonframework.configuration.Configuration} as a parameter to the
 * method to be able to resolve the {@link EventCriteria}. You can also inject the entire configuration as a parameter
 * by declaring it as such. Note that the first parameter must be the identifier, and cannot be a component.
 *
 * @author Mitchell Herrijgers
 * @see TargetEntityId
 * @see EventSourcedEntity
 * @since 5.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventCriteriaBuilder {

}
