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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventCriteria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure several aspects of an event-sourced entity. This annotation is required to construct a
 * {@link AnnotationBasedEventSourcingEntityRepository}.
 * <p>
 * While loading the entity from the {@link EventSourcingRepository}, the provided {@code id} needs to be translated to
 * an {@link EventCriteria} instance to load the correct events. Unless overridden, this translation is done by the
 * {@link AnnotationBasedEventCriteriaResolver}. So, {@link EventCriteria} will be resolved in the following order:
 * <ol>
 *     <li>
 *         Using the custom {@link #criteriaResolver()}. The provided class should implement the {@link CriteriaResolver},
 *         and have a no-arg constructor, or a 1-arg constructor with the {@link Class} of the entity as parameter.
 *     </li>
 *     <li>
 *         By defining a static method in the entity class annotated with {@link EventCriteriaBuilder} which returns an
 *         {@link EventCriteria} and accepts the {@code id} as a parameter. This method should be static and return an
 *         {@link EventCriteria}. Multiple methods can be defined with different id types, and the first matching method
 *         will be used.
 *     </li>
 *     <li>
 *         If no matching {@link EventCriteriaBuilder} is found, the {@link EventSourcedEntity#tagName()} will be used as the tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 *     <li>
 *         If the {@link EventSourcedEntity#tagName()} is empty, the {@link Class#getSimpleName()} of the entity will be used as tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 * </ol>
 *
 * <p>
 * The {@link #entityCreator()} is used to create a new instance of the entity. The provided class should implement the
 * {@link EventSourcedEntityCreator} interface, and have a no-arg constructor, or a 1-arg constructor with the {@link Class} of the entity as parameter.
 * By default, the {@link ConstructorBasedEventSourcedEntityCreator} is used, which creates a new instance using the
 * no-arg constructor of the entity class, or a 1-arg constructor with the id as parameter.
 *
 * @author Mitchell Herrijgers
 * @see EventSourcingRepository
 * @see CriteriaResolver
 * @see AnnotationBasedEventCriteriaResolver
 * @see EventCriteriaBuilder
 * @see EventSourcedEntityCreator
 * @since 5.0.0
 */
@SuppressWarnings("unused")
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventSourcedEntity {

    /**
     * The tag name to use when resolving the {@link EventCriteria} for the entity. If empty, the simple name of the
     * entity class will be used.
     * <p>
     * This value does not take effect if a matching {@link EventCriteriaBuilder} is found, or a custom
     * {@link #criteriaResolver()} is provided.
     *
     * @return The tag name to use when resolving the {@link EventCriteria} for the entity.
     */
    String tagName() default "";

    /**
     * The class to use to resolve the {@link EventCriteria} for the entity. The provided class should implement the
     * {@link CriteriaResolver} interface, and have a no-arg constructor, or a 1-arg constructor with the {@link Class}
     * of the entity as parameter.
     * @return The class to use to resolve the {@link EventCriteria} for the entity.
     */
    Class<? extends CriteriaResolver> criteriaResolver() default AnnotationBasedEventCriteriaResolver.class;

    /**
     * The class to use to create a new instance of the entity. The provided class should implement the
     * {@link EventSourcedEntityCreator} interface, and have a no-arg constructor, or a 1-arg constructor with the
     * {@link Class} of the entity as parameter.
     * @return The class to use to create a new instance of the entity.
     */
    Class<? extends EventSourcedEntityCreator> entityCreator() default ConstructorBasedEventSourcedEntityCreator.class;
}
