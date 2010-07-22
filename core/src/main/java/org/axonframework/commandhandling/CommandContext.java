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

/**
 * Interface describing an object that maintains context information during the command dispatching process.
 *
 * @author Allard Buijze
 * @param <T> The type of result expected from command execution
 * @since 0.5
 */
public interface CommandContext<T> {

    /**
     * Returns the command which has been dispatched for handling.
     *
     * @return the command which has been dispatched for handling.
     */
    Object getCommand();

    /**
     * Returns the value assigned to the property with given <code>name</code>. Returns <code>null</code> if the
     * property has not been set.
     * <p/>
     * No distinction is made between properties with <code>null</code> as their values and properties that have not
     * been set to any specific value. Use {@link #isPropertySet(String)} to make that distinction.
     *
     * @param name The name of the property to retrieve the value for
     * @return the value assigned to the property with given <code>name</code>.
     */
    Object getProperty(String name);

    /**
     * Sets the property with given <code>name</code> to the given <code>value</code>. If a property with that name
     * already exists, it is overwritten with the new value.
     *
     * @param name  The name of the property to set
     * @param value The value to set the property to
     */
    void setProperty(String name, Object value);

    /**
     * Removes the property with given <code>name</code>. If no such property exists, no action is taken.
     *
     * @param name The name of the property to remove
     */
    void removeProperty(String name);

    /**
     * Indicates whether a property with given name exists.
     *
     * @param name the name of the property to check existence of
     * @return <code>true</code> if a property with that name exists, otherwise <code>false</code>.
     */
    boolean isPropertySet(String name);

    /**
     * Indicates whether the command has been passed to the handler for execution. Will always return <code>false</code>
     * in the <code>beforeCommandHandling</code> method. In the <code>afterCommandHandling</code>, it will return
     * <code>true</code> if the handler has been executed, and <code>false</code> if a {@link CommandHandlerInterceptor}
     * has blocked execution.
     *
     * @return <code>true</code> if the handler has executed the command, <code>false</code> otherwise.
     */
    boolean isExecuted();

    /**
     * Indicates whether the execution of the command handler was succesful. Will always return <code>true</code> (even
     * when the command handler has not been executed) unless either the command handler or one of the command handler
     * interceptors threw an exception.
     *
     * @return <code>false</code> if the handler or any of the interceptors threw an exception, <code>true</code>
     *         otherwise.
     */
    boolean isSuccessful();

    /**
     * Returns the result of the command handler invocation. Returns <code>null</code> if the command handler was not
     * invoked or when invocation resulted in an exception.
     *
     * @return the result of the command handler invocation, if any.
     */
    T getResult();

    /**
     * Returns the exception that was thrown by either the command handler or by one of the interceptors. Use the {@link
     * #isExecuted()} method to make the distinction.
     *
     * @return the exception thrown by either the command handler or by one of the interceptors
     */
    Throwable getException();

}
