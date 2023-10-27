/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandMessageHandlingMember;

/**
 * Interface describing a message handler capable of forwarding a specific command.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the command
 * @author Somrak Monpengpinij
 * @since 4.6.0
 */
public interface ForwardingCommandMessageHandlingMember<T> extends CommandMessageHandlingMember<T> {

    /**
     * Check if this handler is in a state where it can currently accept the command.
     *
     * @param message The message that is to be forwarded
     * @param target  The target to forward the command message
     * @return {@code true} if this handler can forward command to target entity, {@code false} otherwise.
     */
    boolean canForward(CommandMessage<?> message, T target);
}
