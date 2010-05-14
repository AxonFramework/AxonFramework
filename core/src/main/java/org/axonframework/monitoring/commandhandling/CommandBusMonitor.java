/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.monitoring.commandhandling;

import org.axonframework.monitoring.Monitor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jettro Coenradie
 */
public class CommandBusMonitor implements Monitor {
    private AtomicLong amountOfHandlers = new AtomicLong(0);
    private AtomicLong amountOfReceivedCommands = new AtomicLong(0);

    public long amountOfHandlers() {
        return amountOfHandlers.get();
    }

    public long amountOfReceivedCommands() {
        return amountOfReceivedCommands.get();
    }

    public void resetReceveidCommandsCounter() {
        amountOfReceivedCommands.set(0);
    }

    public void notifyCommandReceived() {
        amountOfReceivedCommands.getAndIncrement();
    }

    public void notifyNewCommandHandlerSubscribed() {
        amountOfHandlers.getAndIncrement();
    }

    public void notifyCommandHandlerUnSubscribed() {
        amountOfHandlers.getAndDecrement();
    }
}
