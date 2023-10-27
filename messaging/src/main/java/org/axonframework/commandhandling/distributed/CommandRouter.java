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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Optional;

/**
 * Interface describing a mechanism used to find a suitable member of a cluster capable of handling given command
 * message.
 */
public interface CommandRouter {

    /**
     * Returns the member instance to which the given {@code message} should be routed. If no suitable member could be
     * found an empty Optional is returned.
     *
     * @param message the command message to find a member for
     * @return the member that should handle the message or an empty Optional if no suitable member was found
     */
    Optional<Member> findDestination(CommandMessage<?> message);

    /**
     * Updates the load factor and capabilities of this member representing the current endpoint if the implementation
     * allows memberships to be updated dynamically.
     *
     * @param loadFactor    the new load factor of the member for this endpoint
     * @param commandFilter the new capabilities of the member for this endpoint
     */
    void updateMembership(int loadFactor, CommandMessageFilter commandFilter);
}
