/*
 * Copyright (c) 2018-2022. Axon Framework
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

package org.axonframework.axonserver.connector.query;

import org.axonframework.queryhandling.QueryMessage;

/**
 * Calculate the priority of {@link QueryMessage} based on its content. Higher value means higher priority.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@FunctionalInterface
public interface QueryPriorityCalculator {

    /**
     * Determines the priority of the given {@code query}. The higher the returned value, the higher the priority is.
     *
     * @param query a {@link QueryMessage} to prioritize
     * @return an {@code int} defining the priority of the given {@code query}
     */
    int determinePriority(QueryMessage<?, ?> query);

    /**
     * Returns a default implementation of the {@link QueryPriorityCalculator}, always returning priority {@code 0}.
     *
     * @return a lambda taking in a {@link QueryMessage} to prioritize to the default of priority {@code 0}
     */
    static QueryPriorityCalculator defaultQueryPriorityCalculator() {
        return query -> 0;
    }
}
