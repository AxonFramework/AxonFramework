/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.unitofwork;

import org.axonframework.correlation.CorrelationDataProvider;
import org.axonframework.domain.Message;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class represents a UnitOfWork in which modifications are made to aggregates. A typical UnitOfWork scope is the
 * execution of a command. A UnitOfWork may be used to prevent individual events from being published before a number
 * of
 * aggregates has been processed. It also allows repositories to manage resources, such as locks, over an entire
 * transaction. Locks, for example, will only be released when the UnitOfWork is either committed or rolled back.
 * <p/>
 * The current UnitOfWork can be obtained using {@link CurrentUnitOfWork#get()}.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface UnitOfWork {

    /**
     * Commits the UnitOfWork. All registered aggregates that have not been registered as stored are saved in their
     * respective repositories, buffered events are sent to their respective event bus, and all registered
     * UnitOfWorkListeners are notified.
     * <p/>
     * After the commit (successful or not), the UnitOfWork is unregistered from the CurrentUnitOfWork and has cleaned
     * up all resources it occupied. This effectively means that a rollback is done if Unit Of Work failed to commit.
     *
     * @throws IllegalStateException if the UnitOfWork wasn't started
     */
    void commit();

    /**
     * Clear the UnitOfWork of any buffered changes. All buffered events and registered aggregates are discarded and
     * registered {@link org.axonframework.unitofwork.UnitOfWorkListener}s are notified.
     * <p/>
     * If the rollback is a result of an exception, consider using {@link #rollback(Throwable)} instead.
     */
    default void rollback() {
        rollback(null);
    }

    /**
     * Clear the UnitOfWork of any buffered changes. All buffered events and registered aggregates are discarded and
     * registered {@link org.axonframework.unitofwork.UnitOfWorkListener}s are notified.
     *
     * @param cause The cause of the rollback. May be <code>null</code>.
     * @throws IllegalStateException if the UnitOfWork wasn't started
     */
    void rollback(Throwable cause);

    /**
     * Starts the current unit of work, preparing it for aggregate registration. The UnitOfWork instance is registered
     * with the CurrentUnitOfWork.
     */
    void start();

    /**
     * Indicates whether this UnitOfWork is started. It is started when the {@link #start()} method has been called,
     * and
     * if the UnitOfWork has not been committed or rolled back.
     *
     * @return <code>true</code> if this UnitOfWork is started, <code>false</code> otherwise.
     */
    default boolean isActive() {
        return phase().isStarted();
    }

    Phase phase();

    void onPrepareCommit(Consumer<UnitOfWork> handler);

    void onCommit(Consumer<UnitOfWork> handler);

    void afterCommit(Consumer<UnitOfWork> handler);

    void onRollback(BiConsumer<UnitOfWork, Throwable> handler);

    void onCleanup(Consumer<UnitOfWork> handler);

    Optional<UnitOfWork> parent();

    default UnitOfWork root() {
        return parent().map(UnitOfWork::root).orElse(this);
    }

    Message<?> getMessage();

    Map<String, Object> resources();

    default <T> T getOrComputeResource(String key, Function<? super String, T> mappingFunction) {
        return (T) resources().computeIfAbsent(key, mappingFunction);
    }

    default <T> T getOrDefaultResource(String key, T defaultValue) {
        return (T) resources().getOrDefault(key, defaultValue);
    }

    void registerCorrelationDataProvider(CorrelationDataProvider<Message<?>> correlationDataProvider);

    Map<String, ?> getCorrelationData();

    /**
     * Returns the resource previously attached under given <code>name</code>, or <code>null</code> if no such resource
     * is available.
     *
     * @param name The name under which the resource was attached
     * @param <T>  The type of resource
     * @return The resource attached under the given <code>name</code>, or <code>null</code> if no such resource is
     * available.
     */
    default <T> T getResource(String name) {
        return (T) resources().get(name);
    }

    enum Phase {

        NOT_STARTED(false, true),
        STARTED(true, true),
        PREPARE_COMMIT(true, true),
        COMMIT(true, true),
        ROLLBACK(true, false),
        AFTER_COMMIT(true, false),
        CLEANUP(false, false),
        CLOSED(false, false);

        private final boolean started;
        private final boolean callbackOrderAsc;

        Phase(boolean started, boolean callbackOrderAsc) {
            this.started = started;
            this.callbackOrderAsc = callbackOrderAsc;
        }

        public boolean isStarted() {
            return started;
        }

        public boolean isCallbackOrderAsc() {
            return callbackOrderAsc;
        }

        /**
         * Check if this Phase comes before given other <code>phase</code>.
         *
         * @param phase The other Phase
         * @return <code>true</code> if this comes before the given <code>phase</code>, <code>false</code> otherwise.
         */
        public boolean isBefore(Phase phase) {
            return ordinal() < phase.ordinal();
        }

        /**
         * Check if this Phase comes after given other <code>phase</code>.
         *
         * @param phase The other Phase
         * @return <code>true</code> if this comes after the given <code>phase</code>, <code>false</code> otherwise.
         */
        public boolean isAfter(Phase phase) {
            return ordinal() > phase.ordinal();
        }
    }
}
