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
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.mockito.Mockito.*;

class FailingDuplicateQueryHandlerResolverTest {
    private final FailingDuplicateQueryHandlerResolver resolver = FailingDuplicateQueryHandlerResolver.instance();

    private final class MyQuery {}
    private final class MyResponse {}

    @Test
    void throwsErrorOnDuplicateRegistration() {
        QuerySubscription existingHandler = mockSubscription();
        QuerySubscription addedHandler = mockSubscription();

        Assertions.assertThrows(DuplicateQueryHandlerSubscriptionException.class, () -> {
            resolver.resolve("org.axon.MyQuery",
                             MyQuery.class,
                             Collections.singletonList(existingHandler),
                             addedHandler);
        });
    }

    private QuerySubscription mockSubscription() {
        QuerySubscription mock = mock(QuerySubscription.class);
        when(mock.getQueryHandler()).thenReturn(message -> null);
        when(mock.getResponseType()).thenReturn(MyResponse.class);
        return mock;
    }
}
