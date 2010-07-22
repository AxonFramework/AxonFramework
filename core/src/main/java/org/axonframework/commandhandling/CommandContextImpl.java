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
    private boolean executed = false;
    private boolean successful = true;
    private Throwable exception;
    private Object result;

    private final Map<String, Object> properties = new HashMap<String, Object>();

    /**
     * Initialize a context for the given <code>command</code>.
     *
     * @param command The command to be executed
     */
    CommandContextImpl(Object command) {
        this.command = command;
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
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    /**
     * Mark the context as being successfully executed.
     *
     * @param actualResult the result of the handler execution
     */
    public void markSuccessfulExecution(Object actualResult) {
        this.executed = true;
        this.result = actualResult;
    }

    /**
     * Mark the context to indicate an exception was thrown from the command handler.
     *
     * @param actualException the exception thrown from the command handler
     */
    public void markFailedHandlerExecution(Throwable actualException) {
        this.executed = true;
        this.successful = false;
        this.exception = actualException;
    }

    /**
     * Mark the context to indicate an exception was thrown from one of the interceptors.
     *
     * @param actualException The exception thrown from the interceptor
     */
    public void markFailedInterceptorExecution(Throwable actualException) {
        this.successful = false;
        this.exception = actualException;
    }
}
