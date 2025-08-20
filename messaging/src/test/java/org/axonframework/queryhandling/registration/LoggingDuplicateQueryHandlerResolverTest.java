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

package org.axonframework.queryhandling.registration;

import org.axonframework.queryhandling.QuerySubscription;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class LoggingDuplicateQueryHandlerResolverTest {

    private final LoggingDuplicateQueryHandlerResolver resolver = LoggingDuplicateQueryHandlerResolver.instance();

    private static final class MyQuery {

    }

    private static final class MyResponse {
    }

    @Test
    void throwsNoErrorOnDuplicateHandlerAndAddsItToList() {
        QuerySubscription<?> existingHandler = new QuerySubscription<>(MyResponse.class, (m, ctx) -> null);
        QuerySubscription<?> addedHandler = new QuerySubscription<>(MyResponse.class, (m, ctx) -> null);

        CopyOnWriteArrayList<QuerySubscription<?>> existingHandlers = new CopyOnWriteArrayList<>();
        existingHandlers.add(existingHandler);

        List<QuerySubscription<?>> resolvedList =
                resolver.resolve("org.axon.MyQuery", MyQuery.class, existingHandlers, addedHandler);

        assertEquals(2, resolvedList.size());
        assertTrue(resolvedList.contains(existingHandler));
        assertTrue(resolvedList.contains(addedHandler));
    }
}
