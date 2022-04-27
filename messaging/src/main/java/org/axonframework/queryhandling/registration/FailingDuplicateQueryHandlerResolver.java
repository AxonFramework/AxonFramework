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

import java.lang.reflect.Type;
import java.util.List;

/**
 * Implementation of {@link DuplicateQueryHandlerResolver} that throws a {@link DuplicateQueryHandlerSubscriptionException}
 * when a duplicate registration is detected.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class FailingDuplicateQueryHandlerResolver implements DuplicateQueryHandlerResolver {

    private static final FailingDuplicateQueryHandlerResolver INSTANCE = new FailingDuplicateQueryHandlerResolver();

    /**
     * Returns a {@link DuplicateQueryHandlerResolver} that throws an exception when a duplicate registration is
     * detected
     *
     * @return a {@link DuplicateQueryHandlerResolver} that throws an exception when a duplicate registration is
     * detected
     */
    public static FailingDuplicateQueryHandlerResolver instance() {
        return INSTANCE;
    }

    private FailingDuplicateQueryHandlerResolver() {
    }

    @Override
    public List<QuerySubscription<?>> resolve(String queryName,
                                           Type responseType,
                                           List<QuerySubscription<?>> registeredHandlers,
                                           QuerySubscription<?> candidateHandler) {
        throw new DuplicateQueryHandlerSubscriptionException(queryName,
                                                             responseType,
                                                             registeredHandlers.get(0),
                                                             candidateHandler);
    }
}
