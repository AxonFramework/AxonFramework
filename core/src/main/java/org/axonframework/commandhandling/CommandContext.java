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
 * @param <T> The type of command
 * @since 0.5
 */
public interface CommandContext<T> {

    /**
     * Returns the command which has been dispatched for handling.
     *
     * @return the command which has been dispatched for handling.
     */
    T getCommand();

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
     * Returns the command handler chosen to process this command. Interceptors are not supposed to call this command
     * handler directly. They should use the given InterceptorChain and call <code>proceed</code> on that instance
     * instead.
     *
     * @return the command handler chosen to process this command.
     */
    CommandHandler<T> getCommandHandler();
}
