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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This class is responsible for notifying registered listeners in a specific order of precedence.
 * When {@link #onPrepareCommit(UnitOfWork, java.util.Set, java.util.List)}} and
 * {@link #onEventRegistered(UnitOfWork, org.axonframework.domain.EventMessage)} are called the listeners will be
 * handled in the order they have been registered (order of precedence). When {@link #afterCommit(UnitOfWork)}, {@link
 * #onRollback(UnitOfWork, Throwable)}, and {@link #onCleanup(UnitOfWork)} are called the listeners will be handled in
 * the reversed order of precedence.
 * <p/>
 * This behaviour is particularly useful because the {@link org.axonframework.auditing.AuditingUnitOfWorkListener}
 * should write an entry before any other listeners are allowed to anything (prepare commit)
 * and write an entry only after all other listeners have successfully committed (after commit).
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class UnitOfWorkListenerCollection {

    private static final Logger logger = LoggerFactory.getLogger(UnitOfWorkListenerCollection.class);

    private final EnumMap<UnitOfWork.Phase, Deque<Consumer<UnitOfWork>>> listeners = new EnumMap<>(UnitOfWork.Phase.class);
    private final Deque<BiConsumer<UnitOfWork, Throwable>> rollbackListeners = new ArrayDeque<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Deque<Consumer<UnitOfWork>> EMPTY = new LinkedList<>();

    public void invokeListeners(UnitOfWork unitOfWork, Consumer<UnitOfWork.Phase> phaseConsumer,
                                UnitOfWork.Phase... phases) {
        for (UnitOfWork.Phase phase : phases) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listeners for phase {}", phase.toString());
            }
            if (phaseConsumer != null) {
                phaseConsumer.accept(phase);
            }
            if (UnitOfWork.Phase.ROLLBACK.equals(phase)) {
                rollbackListeners.forEach(l -> l.accept(unitOfWork, null));
            }

            Deque<Consumer<UnitOfWork>> l = listeners.getOrDefault(phase, EMPTY);
            while (!l.isEmpty()) {
                l.poll().accept(unitOfWork);
            }
        }
    }

    public void invokeRollbackListeners(UnitOfWork unitOfWork, Throwable e, Consumer<UnitOfWork.Phase> phaseConsumer) {
        phaseConsumer.accept(UnitOfWork.Phase.ROLLBACK);
        listeners.getOrDefault(UnitOfWork.Phase.ROLLBACK, EMPTY).forEach(l -> l.accept(unitOfWork));
        rollbackListeners.forEach(l -> l.accept(unitOfWork, e));
    }

    /**
     * Adds a listener to the collection. Note that the order in which you register the listeners determines the order
     * in which they will be handled during the various stages of a unit of work.
     *
     * @param phase   The phase of the unit of work to attach the handler to
     * @param handler The handler to invoke in the given phase
     */
    public void addListener(UnitOfWork.Phase phase, Consumer<UnitOfWork> handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering listener {} for phase {}", handler.getClass().getName(), phase.toString());
        }
        final Deque<Consumer<UnitOfWork>> consumers = listeners.computeIfAbsent(phase, p -> new ArrayDeque<>());
        if (phase.isCallbackOrderAsc()) {
            consumers.add(handler);
        } else {
            consumers.addFirst(handler);
        }
    }

    public void addRollbackListener(BiConsumer<UnitOfWork, Throwable> handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering listener {} for phase {}",
                         handler.getClass().getName(),
                         UnitOfWork.Phase.ROLLBACK.toString());
        }
        rollbackListeners.addFirst(handler);
    }

    public void clear() {
        listeners.forEach((p, c) -> c.clear());
        rollbackListeners.clear();
    }
}
