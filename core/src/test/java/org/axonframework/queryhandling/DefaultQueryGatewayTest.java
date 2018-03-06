/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

public class DefaultQueryGatewayTest {

    private QueryBus mockBus;
    private DefaultQueryGateway testSubject;
    private QueryResponseMessage<String> answer;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        answer = new GenericQueryResponseMessage<>("answer");
        MessageDispatchInterceptor<QueryMessage<?, ?>> mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
        mockBus = mock(QueryBus.class);
        testSubject = new DefaultQueryGateway(mockBus, mockDispatchInterceptor);
        when(mockDispatchInterceptor.handle(isA(QueryMessage.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    public void testDispatchSingleResultQuery() throws Exception {
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(CompletableFuture.completedFuture(answer));

        CompletableFuture<String> actual = testSubject.query("query", String.class);
        assertEquals("answer", actual.get());

        verify(mockBus).query(argThat(new TypeSafeMatcher<QueryMessage<String, String>>() {
            @Override
            protected boolean matchesSafely(QueryMessage<String, String> item) {
                return "query".equals(item.getPayload());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a QueryMessage containing the 'query' payload");
            }
        }));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDispatchMultiResultQuery() {
        when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Stream<String> actual = testSubject.scatterGather(
                "query", ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS
        );
        assertEquals("answer", actual.findFirst().get());

        verify(mockBus).scatterGather(argThat(new TypeSafeMatcher<QueryMessage<String, String>>() {
            @Override
            protected boolean matchesSafely(QueryMessage<String, String> item) {
                return "query".equals(item.getPayload());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a QueryMessage containing the 'query' payload");
            }
        }), eq(1L), eq(TimeUnit.SECONDS));
    }

    @SuppressWarnings("unused")
    private <Q, R> QueryMessage<Q, R> anyMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }
}
