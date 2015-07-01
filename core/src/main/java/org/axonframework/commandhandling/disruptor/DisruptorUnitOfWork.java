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

import org.axonframework.domain.Message;
import org.axonframework.unitofwork.AbstractUnitOfWork;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;

import java.util.Optional;

/**
 * Specialized UnitOfWork instance for the DisruptorCommandBus. It expects the executing command to target a single
 * aggregate instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorUnitOfWork extends AbstractUnitOfWork {

    private Message<?> message;

    public void reset(Message<?> message) {
        listeners().clear();
        this.message = message;
        setPhase(Phase.NOT_STARTED);
    }

    @Override
    public Optional<UnitOfWork> parent() {
        return Optional.empty();
    }

    public void pause() {
        CurrentUnitOfWork.clear(this);
    }

    public void unpauze() {
        CurrentUnitOfWork.set(this);
    }

    @Override
    public Message<?> getMessage() {
        return message;
    }
}