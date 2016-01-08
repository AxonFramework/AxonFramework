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

package org.axonframework.commandhandling.disruptor;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.AbstractUnitOfWork;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Optional;

/**
 * Specialized UnitOfWork instance for the {@link DisruptorCommandBus}. It expects the executing command message to
 * target a single aggregate instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorUnitOfWork extends AbstractUnitOfWork {

    private Message<?> message;

    /**
     * Resets the state of this Unit of Work, by setting its phase to {@link Phase#NOT_STARTED}, replacing the message
     * of this Unit of Work with given <code>message</code>, and clearing its collection of registered handlers.
     *
     * @param message the new Message that is about to be processed.
     */
    public void reset(Message<?> message) {
        handlers().clear();
        this.message = message;
        setPhase(Phase.NOT_STARTED);
    }

    /**
     * Pause this Unit of Work by unregistering it with the {@link CurrentUnitOfWork}. This will detach it from the
     * current thread.
     */
    public void pause() {
        CurrentUnitOfWork.clear(this);
    }

    /**
     * Resume a paused Unit of Work by registering it with the {@link CurrentUnitOfWork}. This will attach it to the
     * current thread again.
     */
    public void resume() {
        CurrentUnitOfWork.set(this);
    }

    @Override
    public Optional<UnitOfWork> parent() {
        return Optional.empty();
    }

    @Override
    public Message<?> getMessage() {
        return message;
    }
}