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

package org.axonframework.queryhandling.registration;

/**
 * Enumeration describing a set of reasonable {@link DuplicateQueryHandlerResolver} implementations. Can be used to
 * configure how a {@link org.axonframework.queryhandling.QueryBus} should react upon a duplicate subscription of a
 * query handling function.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public abstract class DuplicateQueryHandlerResolution {
    private DuplicateQueryHandlerResolution() {

    }

    /**
     * A {@link DuplicateQueryHandlerResolver} implementation which logs a warning message and resolve to returning
     * both handlers as query handlers.
     *
     * @return an instance that logs duplicates
     */
    public static DuplicateQueryHandlerResolver logAndAccept() {
        return LoggingDuplicateQueryHandlerResolver.instance();
    }

    /**
     * A {@link DuplicateQueryHandlerResolver} implementation which throws a
     * {@link DuplicateQueryHandlerSubscriptionException}.
     *
     * @return an instance that fails on duplicate registrations
     */
    public static DuplicateQueryHandlerResolver rejectDuplicates() {
        return FailingDuplicateQueryHandlerResolver.instance();
    }

    /**
     * A {@link DuplicateQueryHandlerResolver} implementation that allows handlers to silently add previous
     * registered handlers for the same query.
     *
     * @return an instance that silently add duplicates, adding both to the bus
     */
    public static DuplicateQueryHandlerResolver silentlyAdd() {
        return (queryName, type, registered, added) -> {
            registered.add(added);
            return registered;
        };
    }
}
