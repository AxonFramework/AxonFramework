/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.queryhandling.registration;

import org.axonframework.queryhandling.QuerySubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Implementation of the {@link DuplicateQueryHandlerResolver} that allows registrations to be overridden by new handlers,
 * but logs this (on WARN level) to a given logger.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class LoggingDuplicateQueryHandlerResolver implements DuplicateQueryHandlerResolver {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDuplicateQueryHandlerResolver.class);
    private static final LoggingDuplicateQueryHandlerResolver INSTANCE = new LoggingDuplicateQueryHandlerResolver();


    /**
     * Returns an instance that logs duplicate registrations.
     *
     * @return an instance that logs duplicate registrations
     */
    public static LoggingDuplicateQueryHandlerResolver instance() {
        return INSTANCE;
    }

    private LoggingDuplicateQueryHandlerResolver() {
    }

    @Override
    public List<QuerySubscription<?>> resolve(String queryName,
                                           Type responseType,
                                           List<QuerySubscription<?>> registeredHandlers,
                                           QuerySubscription<?> candidateHandler) {

        logger.warn("A duplicate query handler was found for query [{}] and response type [{}]. It has also been registered to the query bus. "
                            + "This is only valid for ScatterGather queries. Normal queries will only use one of these handlers.",
                    queryName, responseType.getTypeName()
        );
        registeredHandlers.add(candidateHandler);
        return registeredHandlers;
    }
}
