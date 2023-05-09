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

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;

/**
 * An {@link AbstractSubscriptionQueryTestSuite} implementation validating the {@link SimpleQueryBus}.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 */
public class SimpleQueryBusSubscriptionQueryTest extends AbstractSubscriptionQueryTestSuite {

    private final SimpleQueryUpdateEmitter queryUpdateEmitter = SimpleQueryUpdateEmitter.builder().build();
    private final SimpleQueryBus queryBus = SimpleQueryBus.builder()
                                                          .queryUpdateEmitter(queryUpdateEmitter)
                                                          .build();

    @Override
    public QueryBus queryBus() {
        return queryBus;
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return queryBus.queryUpdateEmitter();
    }
}
