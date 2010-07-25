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

package org.axonframework.commandhandling;

import java.util.HashMap;
import java.util.Map;

/**
 * CommandContext implementation used by the SimpleCommandBus to track interceptor execution through the dispatching
 * process.
 *
 * @author Allard Buijze
 * @since 0.5
 */
class CommandContextImpl implements CommandContext {

    private final Object command;
    private final CommandHandler commandHandler;

    private final Map<String, Object> properties = new HashMap<String, Object>();

    /**
     * Initialize a context for the given <code>command</code>.
     *
     * @param command The command to be executed
     * @param handler The command handler that will process the command
     */
    CommandContextImpl(Object command, CommandHandler handler) {
        this.command = command;
        this.commandHandler = handler;
    }

    @Override
    public Object getCommand() {
        return command;
    }

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    @Override
    public void removeProperty(String name) {
        properties.remove(name);
    }

    @Override
    public boolean isPropertySet(String name) {
        return properties.containsKey(name);
    }

    @Override
    public CommandHandler getCommandHandler() {
        return commandHandler;
    }
}
