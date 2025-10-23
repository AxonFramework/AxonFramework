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

import org.axonframework.messaging.FluxUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Test utilities when dealing with the {@link QueryBus}.
 *
 * @author Steven van Beelen
 */
public final class QueryBusTestUtils {

    /**
     * Creates a new instance of {@link SimpleQueryBus} configured with a simple
     * {@link UnitOfWorkTestUtils#SIMPLE_FACTORY} and an empty list of processing lifecycle handler registrars.
     *
     * @return An instance of {@link SimpleQueryBus}.
     */
    public static QueryBus aQueryBus() {
        return new SimpleQueryBus(UnitOfWorkTestUtils.SIMPLE_FACTORY);
    }


    private QueryBusTestUtils() {
        // Utility class
    }
}
