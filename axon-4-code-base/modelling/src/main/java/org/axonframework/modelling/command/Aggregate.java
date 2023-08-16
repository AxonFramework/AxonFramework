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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Interface that describes an aggregate. An aggregate is an isolated tree of entities that is capable of handling
 * {@link CommandMessage commands}. Implementations of this interface defer the actual handling of commands to a wrapped
 * instance of type {@code T} or one of its entities.
 * <p>
 * When a command is dispatched to an aggregate Axon will load the aggregate instance and invoke the related command
 * handler method. It is rarely necessary to interact with {@link Aggregate aggregates} directly. Though it is not
 * recommended it is possible to invoke methods on the wrapped instance using {@link #invoke(Function)} and {@link
 * #execute(Consumer)}.
 *
 * @param <T> The aggregate root type
 */
public interface Aggregate<T> {

    /**
     * Get the String representation of the aggregate's type. This defaults to the simple name of the {@link
     * #rootType()} unless configured otherwise.
     *
     * @return The aggregate's type
     */
    String type();

    /**
     * Get the unique identifier of this aggregate, represented as a String.
     *
     * @return The aggregate's identifier as a String
     */
    default String identifierAsString() {
        return Objects.toString(identifier(), null);
    }

    /**
     * Get the unique identifier of this aggregate
     *
     * @return The aggregate's identifier
     */
    Object identifier();

    /**
     * Get the aggregate's version. For event sourced aggregates this is identical to the sequence number of the last
     * applied event.
     *
     * @return The aggregate's version
     */
    Long version();

    /**
     * Handle the given {@code message} on the aggregate root or one of its child entities.
     *
     * @param message The message to be handled by the aggregate
     * @return The result of message handling. Might returns {@code null} if for example handling a
     * {@link CommandMessage} yields no results
     *
     * @throws Exception in case one is triggered during message processing
     */
    Object handle(Message<?> message) throws Exception;

    /**
     * Invoke a method on the underlying aggregate root or one of its instances. Use this over {@link
     * #execute(Consumer)} to obtain an invocation result, for instance in order to query the aggregate.
     * <p>
     * Note that the use of this method is not recommended as aggregates are not meant to be queried. Relying on this
     * method is commonly a sign of design smell.
     *
     * @param invocation The function that performs the actual invocation
     * @param <R>        The type of the result produced by the given invocation
     * @return The invocation result
     */
    <R> R invoke(Function<T, R> invocation);

    /**
     * Execute a method on the underlying aggregate or one of its instances.
     * <p>
     * Note that the use of this method is not recommended as the wrapped aggregate instance is not meant to be
     * exposed. Relying on this method is commonly a sign of design smell.
     *
     * @param invocation The function that performs the invocation
     */
    void execute(Consumer<T> invocation);

    /**
     * Check if this aggregate has been deleted. This is checked by aggregate repositories when an aggregate is loaded.
     * In case the repository is asked to load a deleted aggregate the repository will refuse by throwing an {@link
     * org.axonframework.eventsourcing.AggregateDeletedException}.
     *
     * @return {@code true} in case the aggregate was deleted, {@code false} otherwise
     */
    boolean isDeleted();

    /**
     * Get the class type of the wrapped aggregate root that the Aggregate defers to for command handling.
     *
     * @return The aggregate root type
     */
    Class<? extends T> rootType();
}
