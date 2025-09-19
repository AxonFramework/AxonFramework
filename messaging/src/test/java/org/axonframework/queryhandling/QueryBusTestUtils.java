/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;

/**
 * Test utilities when dealing with the {@link QueryBus}.
 *
 * @author Steven van Beelen
 */
public class QueryBusTestUtils {

    /**
     * Creates a new instance of {@link SimpleCommandBus} configured with a simple
     * {@link UnitOfWorkTestUtils#SIMPLE_FACTORY} and an empty list of processing lifecycle handler registrars.
     *
     * @return An instance of {@link SimpleCommandBus}.
     */
    public static QueryBus aQueryBus() {
        return new SimpleQueryBus(
                UnitOfWorkTestUtils.SIMPLE_FACTORY,
                new SimpleQueryUpdateEmitter()
        );
    }

    private QueryBusTestUtils() {
        // Utility class
    }
}
