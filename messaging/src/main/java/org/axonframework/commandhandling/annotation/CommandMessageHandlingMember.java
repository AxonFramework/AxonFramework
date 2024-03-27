/*
 * Copyright (c) 2010-2024. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.annotation;

import org.axonframework.messaging.annotation.MessageHandlingMember;

/**
 * Interface describing a message handler capable of handling a specific command.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the command
 */
public interface CommandMessageHandlingMember<T> extends MessageHandlingMember<T> {

    /**
     * Returns the name of the command that can be handled.
     *
     * @return The name of the command that can be handled
     */
    String commandName();

    /**
     * Returns the property of the command that is to be used as routing key towards this command handler instance. If
     * multiple handlers instances are available, a sending component is responsible to route commands with the same
     * routing key value to the correct instance.
     *
     * @return The property of the command to use as routing key
     */
    String routingKey();

    /**
     * Check if this message handler creates a new instance of the entity of type {@code T} to handle this command.
     * <p>
     * This is for instance the case if the message is handled in the constructor method of the entity.
     *
     * @return {@code true} if this handler is also factory for entities, {@code false} otherwise.
     */
    boolean isFactoryHandler();
}
