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

package org.axonframework.messaging.unitofwork;

import org.axonframework.messaging.unitofwork.UnitOfWork.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This class is responsible for notifying handlers registered with a Unit of Work when the Unit of Work transitions
 * to a new {@link Phase}.
 * <p/>
 * Depending on the {@link Phase} of the Unit of Work the registered handlers will be invoked in the order in which
 * they have been registered, or in the reverse order. See {@link Phase} for more information.
 *
 * @author Frank Versnel
 * @since 2.0
 * @see Phase
 */
public class UnitOfWorkHandlerCollection {

    private static final Logger logger = LoggerFactory.getLogger(UnitOfWorkHandlerCollection.class);
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Deque<Consumer<UnitOfWork>> EMPTY = new LinkedList<>();

    private final EnumMap<Phase, Deque<Consumer<UnitOfWork>>> handlers = new EnumMap<>(Phase.class);
    private final Deque<BiConsumer<UnitOfWork, Throwable>> rollbackHandlers = new ArrayDeque<>();

    /**
     * Invoke the handlers in this collection attached to given <code>phases</code>. After the handlers are invoked
     * the <code>phaseConsumer</code> is used to update the phase of the Unit of Work.
     *
     * @param unitOfWork    The Unit of Work that is changing its phase
     * @param phaseConsumer The process that sets the new phase on the Unit of Work
     * @param phases        The phases for which attached handlers should be invoked
     */
    public void invokeHandlers(UnitOfWork unitOfWork, Consumer<Phase> phaseConsumer,
                               Phase... phases) {
        for (Phase phase : phases) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying handlers for phase {}", phase.toString());
            }
            if (phaseConsumer != null) {
                phaseConsumer.accept(phase);
            }
            if (Phase.ROLLBACK.equals(phase)) {
                rollbackHandlers.forEach(l -> l.accept(unitOfWork, null));
            }

            Deque<Consumer<UnitOfWork>> l = handlers.getOrDefault(phase, EMPTY);
            while (!l.isEmpty()) {
                l.poll().accept(unitOfWork);
            }
        }
    }

    /**
     * Invoke the rollback handlers in this collection after a rollback of the Unit of Work. After the handlers
     * are invoked the <code>phaseConsumer</code> is used to update the phase of the Unit of Work.
     *
     * @param unitOfWork    The Unit of Work that is being rolled back
     * @param e             The cause of the rollback
     * @param phaseConsumer The process that sets the new phase on the Unit of Work
     */
    public void invokeRollbackListeners(UnitOfWork unitOfWork, Throwable e, Consumer<Phase> phaseConsumer) {
        phaseConsumer.accept(Phase.ROLLBACK);
        handlers.getOrDefault(Phase.ROLLBACK, EMPTY).forEach(l -> l.accept(unitOfWork));
        rollbackHandlers.forEach(l -> l.accept(unitOfWork, e));
    }

    /**
     * Adds a handler to the collection. Note that the order in which you register the handlers determines the order
     * in which they will be handled during the various stages of a unit of work.
     *
     * @param phase   The phase of the unit of work to attach the handler to
     * @param handler The handler to invoke in the given phase
     */
    public void addHandler(Phase phase, Consumer<UnitOfWork> handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding handler {} for phase {}", handler.getClass().getName(), phase.toString());
        }
        final Deque<Consumer<UnitOfWork>> consumers = handlers.computeIfAbsent(phase, p -> new ArrayDeque<>());
        if (phase.isReverseCallbackOrder()) {
            consumers.add(handler);
        } else {
            consumers.addFirst(handler);
        }
    }

    /**
     * Adds a handler to the collection that will be invoked on rollback of the Unit of Work. Note that the order in
     * which you register the handlers determines the order in which they will be handled during the various stages
     * of a unit of work.
     *
     * @param handler The handler to invoke in the {@link Phase#ROLLBACK} phase
     */
    public void addRollbackHandler(BiConsumer<UnitOfWork, Throwable> handler) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding handler {} for phase {}", handler.getClass().getName(), Phase.ROLLBACK.toString());
        }
        rollbackHandlers.addFirst(handler);
    }

    /**
     * Clear the collection of handlers.
     */
    public void clear() {
        handlers.forEach((p, c) -> c.clear());
        rollbackHandlers.clear();
    }
}
