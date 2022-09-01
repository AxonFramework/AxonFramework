/*
 * Copyright (c) 2010-2022. Axon Framework
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

/**
 * Enumeration describing a set of reasonable {@link DuplicateCommandHandlerResolver} implementations. Can be used to
 * configure how a {@link org.axonframework.commandhandling.CommandBus} should react upon a duplicate subscription of a
 * command handling function.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
public abstract class DuplicateCommandHandlerResolution {
    private DuplicateCommandHandlerResolution() {
    }

    /**
     * A {@link DuplicateCommandHandlerResolver} implementation which logs a warning message and resolve to returning
     * the duplicate handler and overriding the existing command handler.
     *
     * @return an instance that logs duplicates
     */
    public static DuplicateCommandHandlerResolver logAndOverride() {
        return LoggingDuplicateCommandHandlerResolver.instance();
    }

    /**
     * A {@link DuplicateCommandHandlerResolver} implementation which throws a
     * {@link DuplicateCommandHandlerSubscriptionException}.
     *
     * @return an instance that fails on duplicate registrations
     */
    public static DuplicateCommandHandlerResolver rejectDuplicates() {
        return FailingDuplicateCommandHandlerResolver.instance();
    }

    /**
     * A {@link DuplicateCommandHandlerResolver} implementation that allows handlers to silently override previous
     * registered handlers for the same command.
     *
     * @return an instance that silently accepts duplicates
     */
    public static DuplicateCommandHandlerResolver silentOverride() {
        return (cmd, first, second) -> second;
    }
}
