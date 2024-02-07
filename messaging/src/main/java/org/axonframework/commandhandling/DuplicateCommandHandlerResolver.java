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

package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageHandler;

import javax.annotation.Nonnull;

/**
 * Functional interface towards resolving the occurrence of a duplicate command handler being subscribed. As such it
 * ingests two {@link MessageHandler} instances and returns another one as the resolution.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
@Deprecated // duplicate registrations should always lead to errors
@FunctionalInterface
public interface DuplicateCommandHandlerResolver {

    /**
     * Chooses what to do when a duplicate handler is registered, returning the handler that should be selected for
     * command handling, or otherwise throwing an exception to reject registration altogether.
     *
     * @param commandName       The name of the Command for which the duplicate was detected
     * @param registeredHandler the {@link MessageHandler} previously registered with the Command Bus
     * @param candidateHandler  the {@link MessageHandler} that is newly registered and conflicts with the existing
     *                          registration
     * @return the resolved {@link MessageHandler}. Could be the {@code registeredHandler}, the {@code candidateHandler}
     * or another handler entirely
     * @throws RuntimeException when registration should fail
     */
    MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> resolve(@Nonnull String commandName,
                                                      @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> registeredHandler,
                                                      @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> candidateHandler);
}
