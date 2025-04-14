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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EntityEvolver;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.configuration.EntityBuilder;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

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
                @Nonnull ComponentFactory<EventSourcedEntityFactory<E, I>> entityFactory
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
     * {@link #entityEvolver(ComponentFactory) entity evolver} can be defined, after which the builder is resolved. The
     * second option allows for providing several separate
     * {@link #eventSourcingHandler(QualifiedName, Class, BiConsumer) event sourcing handlers} that will be combined by
     * the builder into an {@link EntityEvolver}.
     * <p>
     * The {@code EntityEvolver} is the component that rehydrates the entity of type {@code E} based on all the events
     * that are sourced as a consequence of the {@link org.axonframework.eventsourcing.eventstore.EventCriteria}
     * returned by the {@link CriteriaResolver}.
     * <p>
     * Note that the setting a single {@code EntityEvolver} will replace <b>any</b> registered event sourcing handlers.
     *
     * @param <I> The type of identifier used to identify the event sourced entity that's being built.
     * @param <E> The type of the event sourced entity being built.
     */
    interface EventSourcingHandlerPhase<I, E> extends EventSourcedEntityBuilder<I, E> {

        /**
         * Register the given {@code entityEvolver} as a factory method for the event sourced entity being built.
         * <p>
         * The {@code EntityEvolver} is the component that rehydrates the entity of type {@code E} based on all the
         * events that are sourced as a consequence of the
         * {@link org.axonframework.eventsourcing.eventstore.EventCriteria} returned by the {@link CriteriaResolver}.
         * While doing so, it {@link EntityEvolver#evolve(Object, EventMessage, ProcessingContext) evolves} the entity
         * one event at a time.
         * <p>
         * Note that invoking this method will replace <b>any</b> registered
         * {@link #eventSourcingHandler(QualifiedName, Class, BiFunction) event sourcing handlers}!
         *
         * @param entityEvolver A factory method constructing the {@link EntityEvolver} for the event sourced entity
         *                      being built.
         * @return The parent {@link EventSourcedEntityBuilder}, signaling the end of configuring an event sourced
         * entity.
         */
        EventSourcedEntityBuilder<I, E> entityEvolver(@Nonnull ComponentFactory<EntityEvolver<E>> entityEvolver);

        /**
         * Registers the given {@code eventSourcingHandler} for the given {@code payloadType}.
         * <p>
         * The {@link QualifiedName} the given {@code eventSourcingHandler} reacts on is constructed through the
         * {@link QualifiedName#QualifiedName(Class)} constructor.
         * <p>
         * If you prefer to return immutable event sourced entities on each evolve step, consider using
         * {@link #eventSourcingHandler(Class, BiFunction)} instead.
         *
         * @param payloadType          The type of the payload of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @param eventSourcingHandler The event sourcing handler to register for the entity being built.
         * @param <P>                  The type of the payload of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @return This event sourcing handler phase, allowing for a fluent API to register several event sourcing
         * handlers.
         * @throws IllegalArgumentException If there already is a matching {@link QualifiedName} based on the given
         *                                  {@code payloadType} registered with this builder.
         */
        default <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(
                @Nonnull Class<P> payloadType,
                @Nonnull BiConsumer<E, P> eventSourcingHandler
        ) {
            return eventSourcingHandler(new QualifiedName(payloadType), payloadType, eventSourcingHandler);
        }

        /**
         * Registers the given {@code eventSourcingHandler} for the given qualified {@code eventName} and
         * {@code payloadType}.
         * <p>
         * Events matching both the given {@code eventName} and {@code payloadType} will be handled by the given
         * {@code eventSourcingHandler}.
         * <p>
         * If you prefer to return immutable event sourced entities on each evolve step, consider using
         * {@link #eventSourcingHandler(QualifiedName, Class, BiFunction)} instead.
         *
         * @param eventName            The qualified name of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @param payloadType          The type of the payload of the event the given {@code eventHandler} can handle.
         * @param eventSourcingHandler The event sourcing handler to register for the entity being built.
         * @param <P>                  The type of the payload of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @return This event sourcing handler phase, allowing for a fluent API to register several event sourcing
         * handlers.
         * @throws IllegalArgumentException If there already is a matching {@link QualifiedName} based on the given
         *                                  {@code eventName} registered with this builder.
         */
        default <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(
                @Nonnull QualifiedName eventName,
                @Nonnull Class<P> payloadType,
                @Nonnull BiConsumer<E, P> eventSourcingHandler
        ) {
            return eventSourcingHandler(eventName, payloadType, (entity, payload) -> {
                Objects.requireNonNull(eventSourcingHandler, "The event sourcing handler must not be null.")
                       .accept(entity, payload);
                return entity;
            });
        }

        /**
         * Registers the given {@code eventSourcingHandler} for the given {@code payloadType}.
         * <p>
         * The {@link QualifiedName} the given {@code eventSourcingHandler} reacts on is constructed through the
         * {@link QualifiedName#QualifiedName(Class)} constructor.
         * <p>
         * You can return a new instance of the event sourced entity in the {@code eventSourcingHandler} to support
         * immutable entities.
         *
         * @param payloadType          The type of the payload of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @param eventSourcingHandler The event sourcing handler to register for the entity being built.
         * @param <P>                  The type of the payload of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @return This event sourcing handler phase, allowing for a fluent API to register several event sourcing
         * handlers.
         * @throws IllegalArgumentException If there already is a matching {@link QualifiedName} based on the given
         *                                  {@code payloadType} registered with this builder.
         */
        default <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(
                @Nonnull Class<P> payloadType,
                @Nonnull BiFunction<E, P, E> eventSourcingHandler
        ) {
            return eventSourcingHandler(new QualifiedName(payloadType), payloadType, eventSourcingHandler);
        }

        /**
         * Registers the given {@code eventSourcingHandler} for the given qualified {@code eventName} and
         * {@code payloadType}.
         * <p>
         * Events matching both the given {@code eventName} and {@code payloadType} will be handled by the given
         * {@code eventSourcingHandler}.
         * <p>
         * You can return a new instance of the event sourced entity in the {@code eventSourcingHandler} to support
         * immutable entities.
         *
         * @param eventName            The qualified name of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @param payloadType          The type of the payload of the event the given {@code eventHandler} can handle.
         * @param eventSourcingHandler The event sourcing handler to register for the entity being built.
         * @param <P>                  The type of the payload of the event the given {@code eventSourcingHandler} can
         *                             handle.
         * @return This event sourcing handler phase, allowing for a fluent API to register several event sourcing
         * handlers.
         * @throws IllegalArgumentException If there already is a matching {@link QualifiedName} based on the given
         *                                  {@code eventName} registered with this builder.
         */
        <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(
                @Nonnull QualifiedName eventName,
                @Nonnull Class<P> payloadType,
                @Nonnull BiFunction<E, P, E> eventSourcingHandler
        );
    }
}
