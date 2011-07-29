/*
 * Copyright (c) 2010-2011. Axon Framework
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

import com.lmax.disruptor.BatchHandler;
import org.axonframework.commandhandling.CommandHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Allard Buijze
 */
class CommandHandlerResolver implements BatchHandler<CommandHandlingEntry> {

    private final Map<Class<?>, CommandHandler<?>> commandHandlers;

    public CommandHandlerResolver(Map<Class<?>, CommandHandler<?>> commandHandlers) {
        this.commandHandlers = new HashMap<Class<?>, CommandHandler<?>>(commandHandlers);
    }

    @Override
    public void onAvailable(CommandHandlingEntry entry) throws Exception {
        entry.setCommandHandler(commandHandlers.get(entry.getCommand().getClass()));
        entry.setUnitOfWork(new MultiThreadedUnitOfWork());
    }

    @Override
    public void onEndOfBatch() throws Exception {
    }
}
