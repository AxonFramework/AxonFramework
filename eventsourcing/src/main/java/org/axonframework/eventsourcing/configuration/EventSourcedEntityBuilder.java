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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.configuration.EntityBuilder;

/**
 * An expansion on the {@link EntityBuilder}, specifically for event sourced entities.
 * <p>
 * Invoke the {@code static} {@link #entity(Class, Class)} operation to start the builder flow for an event sourced
 * entity.
 * <p>
 * Provides operations to guide users to provide the necessary
 * {@link EntityFactoryPhase#entityFactory(ComponentFactory) entity factory} and
 * {@link CriteriaResolverPhase#criteriaResolver(ComponentFactory) criteria resolver} (used to source the entity from an
 * {@link org.axonframework.eventsourcing.eventstore.AsyncEventStore}) before expecting event sourcing handler
 * registration.
 * <p>
 * The separate methods of this builder ensure that the bare minimum required to provide the {@link #entityName()} and
 * {@link #repository()} are present at the end.
 *
 * @param <I> The type of identifier used to identify the event sourced entity that's being built.
 * @param <E> The type of the event sourced entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventSourcedEntityBuilder<I, E> extends EntityBuilder<I, E> {

    /**
     * Starts the builder for an event sourced entity with the given {@code entityType} and {@code idType}.
     *
     * @param idType     The type of identifier used to identify the event sourced entity that's being built.
     * @param entityType The type of the event sourced entity being built.
     * @param <I>        The type of identifier used to identify the event sourced entity that's being built.
     * @param <E>        The type of the event sourced entity being built.
     * @return The entity factory phase of this builder, for a fluent API.
     */
    static <I, E> EntityFactoryPhase<I, E> entity(@Nonnull Class<I> idType,
                                                  @Nonnull Class<E> entityType) {
        return new DefaultEventSourcedEntityBuilder<>(idType, entityType);
    }

    /**
     * Starts the builder for an annotated event sourced entity with the given {@code entityType} and {@code idType}.
     * <p>
     * The given {@code entityType} is expected to be annotated with
     * {@link org.axonframework.eventsourcing.annotation.EventSourcedEntity}. This annotation will allow for retrieval
     * of the {@link org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory} and {@link CriteriaResolver}
     * to construct the {@link org.axonframework.eventsourcing.AsyncEventSourcingRepository} for the event sourced
     * entity being built.
     *
     * @param idType     The type of identifier used to identify the annotated event sourced entity that's being built.
     * @param entityType The type of the annotated event sourced entity being built.
     * @param <I>        The type of identifier used to identify the event sourced entity that's being built.
     * @param <E>        The type of the event sourced entity being built.
     * @return The event sourced entity builder, signaling the end of configuring an annotated event sourced entity.
     * @throws IllegalArgumentException When the given {@code entityType} is not annotated with
     *                                  {@link org.axonframework.eventsourcing.annotation.EventSourcedEntity}.
     */
    static <I, E> EventSourcedEntityBuilder<I, E> annotatedEntity(@Nonnull Class<I> idType,
                                                                  @Nonnull Class<E> entityType) {
        return new AnnotatedEventSourcedEntityBuilder<>(idType, entityType);
    }

    /**
     * The entity factory phase of the event sourced entity builder.
     * <p>
     * Enforce providing the {@link #entityFactory(ComponentFactory)} for the event sourced entity that's being built.
     *
     * @param <I> The type of identifier used to identify the event sourced entity that's being built.
     * @param <E> The type of the event sourced entity being built.
     */
    interface EntityFactoryPhase<I, E> {

        /**
         * Registers the given {@code entityFactory} as a factory method for the event sourced entity being built.
         * <p>
         * The resulting {@link EventSourcedEntityFactory} from the component factory receives the entity type {@code E}
         * and the identifier of type {@code I} and expects an event sourced entity instance of type {@code E} as a
         * result. The {@link org.axonframework.configuration.NewConfiguration} in the component factory allows the
         * entity factory to use other components that have been registered.
         *
         * @param entityFactory A factory method constructing the entity of type {@code E} based on the entity's type
         *                      and an identifier of type {@code I}.
         * @return The {@link CriteriaResolver} phase of this builder, for a fluent API.
         */
        CriteriaResolverPhase<I, E> entityFactory(
                @Nonnull ComponentFactory<EventSourcedEntityFactory<I, E>> entityFactory
        );
    }

    /**
     * The {@link CriteriaResolver} phase of the event sourced entity builder.
     * <p>
     * Enforces providing the {@link #criteriaResolver(ComponentFactory) criteria resolver} for the event sourced entity
     * that's being built. A {@code CriteriaResolver} receives the entity's identifier of type {@code I} and expects the
     * {@link org.axonframework.eventsourcing.eventstore.EventCriteria} as as result. The resulting
     * {@code EventCriteria} is used to
     * {@link org.axonframework.eventsourcing.eventstore.EventStoreTransaction#source(SourcingCondition) source} the
     * entity from the {@link org.axonframework.eventsourcing.eventstore.AsyncEventStore}.
     *
     * @param <I> The type of identifier used to identify the event sourced entity that's being built.
     * @param <E> The type of the event sourced entity being built.
     */
    interface CriteriaResolverPhase<I, E> {

        /**
         * Registers the given {@code criteriaResolver} as a factory method for the event sourced entity being built.
         * <p>
         * A {@code CriteriaResolver} receives the entity's identifier of type {@code I} and expects the
         * {@link org.axonframework.eventsourcing.eventstore.EventCriteria} as as result. The resulting
         * {@code EventCriteria} is used to
         * {@link org.axonframework.eventsourcing.eventstore.EventStoreTransaction#source(SourcingCondition) source} the
         * entity from the {@link org.axonframework.eventsourcing.eventstore.AsyncEventStore}.
         *
         * @param criteriaResolver A factory method constructing the {@link CriteriaResolver}, used to resolve the
         *                         {@link org.axonframework.eventsourcing.eventstore.EventCriteria} based on the
         *                         identifier of type {@code I} to source the entity.
         * @return The event sourcing handler phase of the builder, for a fluent API.
         */
        EventSourcingHandlerPhase<I, E> criteriaResolver(
                @Nonnull ComponentFactory<CriteriaResolver<I>> criteriaResolver
        );
    }

    /**
     * The event sourcing handler phase of the event sourced entity builder.
     * <p>
     * Allows for two paths when building an event sourced entity. Firstly, a
     * {@link #eventStateApplier(ComponentFactory) event state applier} can be defined, after which the builder is
     * resolved. The second option allows for providing several separate
     * {@link #eventSourcingHandler(QualifiedName, EventHandler) event sourcing handlers} that will be combined by the
     * builder into an {@link EventStateApplier}.
     * <p>
     * The {@code EventStateApplier} is the component that rehydrates the entity of type {@code E} based on all the
     * events that are sourced as a consequence of the {@link org.axonframework.eventsourcing.eventstore.EventCriteria}
     * returned by the {@link CriteriaResolver}.
     *
     * @param <I> The type of identifier used to identify the event sourced entity that's being built.
     * @param <E> The type of the event sourced entity being built.
     */
    interface EventSourcingHandlerPhase<I, E> extends EventSourcedEntityBuilder<I, E> {

        /**
         * Register the given {@code eventStateApplier} as a factory method for the event sourced entity being built.
         * <p>
         * The {@code EventStateApplier} is the component that rehydrates the entity of type {@code E} based on all the
         * events that are sourced as a consequence of the
         * {@link org.axonframework.eventsourcing.eventstore.EventCriteria} returned by the {@link CriteriaResolver}.
         *
         * @param eventStateApplier A factory method constructing the {@link EventStateApplier} for the event sourced
         *                          entity being built.
         * @return The parent {@link EventSourcedEntityBuilder}, signaling the end of configuring an event sourced
         * entity.
         */
        EventSourcedEntityBuilder<I, E> eventStateApplier(
                @Nonnull ComponentFactory<EventStateApplier<E>> eventStateApplier
        );

        /**
         * Registers the given {@code eventHandler} for the given qualified {@code eventName} within this event sourced
         * entity builder.
         * <p>
         * All invocations of this method are combined into a single {@link EventStateApplier} for
         * {@link org.axonframework.eventsourcing.AsyncEventSourcingRepository} outputted by this event sourced entity
         * builder.
         *
         * @param eventName    The qualified name of the event the given {@code eventHandler} can handle.
         * @param eventHandler The event sourcing handler to register for the entity being built.
         * @return This event sourcing handler phase, allowing for a fluent API to register several event sourcing
         * handlers.
         */
        EventSourcingHandlerPhase<I, E> eventSourcingHandler(@Nonnull QualifiedName eventName,
                                                             @Nonnull EventHandler eventHandler);
    }
}
