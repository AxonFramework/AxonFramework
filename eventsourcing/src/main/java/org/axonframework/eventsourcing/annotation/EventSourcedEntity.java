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
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventCriteria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure several aspects of an event-sourced entity. This annotation is required to construct an
 * annotation-based entity through the
 * {@link org.axonframework.eventsourcing.configuration.EventSourcedEntityBuilder#annotatedEntity(Class, Class)}.
 * <p>
 * While loading the entity from the {@link AsyncEventSourcingRepository}, the provided {@code id} needs to be
 * translated to an {@link EventCriteria} instance to load the correct events. Unless overridden, this translation is
 * done by the {@link AnnotationBasedEventCriteriaResolver}. So, {@link EventCriteria} will be resolved in the following
 * order:
 * <ol>
 *     <li>
 *         Using the custom {@link #criteriaResolverDefinition()}. The definition should return a {@link CriteriaResolver},
 *         implement the {@link CriteriaResolverDefinition} interface, and have a no-arg constructor.
 *     </li>
 *     <li>
 *         By defining a static method in the entity class annotated with {@link EventCriteriaBuilder} which returns an
 *         {@link EventCriteria} and accepts the {@code id} as a parameter. This method should be static and return an
 *         {@link EventCriteria}. Multiple methods can be defined with different id types, and the first matching method
 *         will be used. Optionally, you can define {@link org.axonframework.messaging.MessageTypeResolver} as a second
 *         parameter to resolve the type of the message. Other arguments are not supported.
 *     </li>
 *     <li>
 *         If no matching {@link EventCriteriaBuilder} is found, the {@link EventSourcedEntity#tagKey()} will be used as the tag key, and the {@link Object#toString()} of the id will be used as value.
 *     </li>
 *     <li>
 *         If the {@link EventSourcedEntity#tagKey()} is empty, the {@link Class#getSimpleName()} of the entity will be used as tag key, and the {@link Object#toString()} of the id will be used as value.
 *         Note that the tag format rules are undecided until <a href="https://github.com/AxonFramework/AxonFramework/issues/3326">issue 3326/a> is resolved. This may change in the future.
 *     </li>
 * </ol>
 *
 * <p>
 * The {@link #entityFactoryDefinition()} is used to create a new instance of the entity. The provided class should implement the
 * {@link EventSourcedEntityFactory} interface, and have a no-arg constructor, or a 1-arg constructor with the {@link Class} of the entity as parameter.
 * By default, the {@link ConstructorBasedEventSourcedEntityFactory} is used, which creates a new instance using the
 * no-arg constructor of the entity class, or a 1-arg constructor with the id as parameter.
 *
 * @author Mitchell Herrijgers
 * @see AsyncEventSourcingRepository
 * @see CriteriaResolver
 * @see AnnotationBasedEventCriteriaResolver
 * @see EventCriteriaBuilder
 * @see EventSourcedEntityFactory
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
     * {@link #criteriaResolverDefinition()} is provided.
     *
     * @return The tag name to use when resolving the {@link EventCriteria} for the entity.
     */
    String tagKey() default "";

    /**
     * The definition of the {@link CriteriaResolver} to use to resolve the {@link EventCriteria} for the entity. A
     * custom definition can be provided to override the default behavior of the
     * {@link AnnotationBasedEventCriteriaResolver}.
     *
     * @return The definition to construct a {@link CriteriaResolverDefinition}.
     */
    Class<? extends CriteriaResolverDefinition> criteriaResolverDefinition() default AnnotationBasedEventCriteriaResolverDefinition.class;

    /**
     * The definition of the {@link EventSourcedEntityFactory} to use to create a new instance of the entity. A custom
     * definition can be provided to override the default behavior of the
     * {@link ConstructorBasedEventSourcedEntityFactory}.
     *
     * @return The definition to construct an {@link EventSourcedEntityFactory}.
     */
    Class<? extends EventSourcedEntityFactoryDefinition> entityFactoryDefinition() default ConstructorBasedEventSourcedEntityFactoryDefinition.class;
}
