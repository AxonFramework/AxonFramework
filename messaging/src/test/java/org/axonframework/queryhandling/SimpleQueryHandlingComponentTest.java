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

import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleQueryHandlingComponent}.
 *
 * @author Steven van Beelen
 */
class SimpleQueryHandlingComponentTest {

    private static final MessageType RESPONSE_TYPE = new MessageType(String.class);

    private final AtomicBoolean query1Handled = new AtomicBoolean(false);
    private final AtomicBoolean query2HandledParent = new AtomicBoolean(false);
    private final AtomicBoolean query2HandledChild = new AtomicBoolean(false);
    private final AtomicBoolean query3Handled = new AtomicBoolean(false);

    private final SimpleQueryHandlingComponent handlingComponent = SimpleQueryHandlingComponent
            .create("MySuperComponent")
            .subscribe(
                    new QualifiedName("Query1"), new QualifiedName(String.class),
                    (query, context) -> {
                        query1Handled.set(true);
                        return MessageStream.empty().cast();
                    }
            )
            .subscribe(
                    new QualifiedName("Query2"), new QualifiedName(String.class),
                    (query, context) -> {
                        query2HandledParent.set(true);
                        return MessageStream.empty().cast();
                    }
            )
            .subscribe(
                    SimpleQueryHandlingComponent
                            .create("MySubComponent")
                            .subscribe(
                                    new QualifiedName("Query2"), new QualifiedName(String.class),
                                    (query, context) -> {
                                        query2HandledChild.set(true);
                                        return MessageStream.empty().cast();
                                    }
                            )
                            .subscribe(
                                    new QualifiedName("Query3"), new QualifiedName(String.class),
                                    (query, context) -> {
                                        query3Handled.set(true);
                                        return MessageStream.empty().cast();
                                    }
                            )
            );

    @Test
    void handlesTheMostSpecificRegisteredHandler() {
        QueryMessage query1 = new GenericQueryMessage(new MessageType("Query1"), "", RESPONSE_TYPE);
        handlingComponent.handle(query1, StubProcessingContext.forMessage(query1));
        assertTrue(query1Handled.get());
        assertFalse(query2HandledParent.get());
        assertFalse(query2HandledChild.get());
        assertFalse(query3Handled.get());

        query1Handled.set(false);

        QueryMessage query2 = new GenericQueryMessage(new MessageType("Query2"), "", RESPONSE_TYPE);
        handlingComponent.handle(query2, StubProcessingContext.forMessage(query2));
        assertFalse(query1Handled.get());
        assertFalse(query2HandledParent.get());
        assertTrue(query2HandledChild.get());
        assertFalse(query3Handled.get());

        query2HandledChild.set(false);
        QueryMessage query3 = new GenericQueryMessage(new MessageType("Query3"), "", RESPONSE_TYPE);
        handlingComponent.handle(query3, StubProcessingContext.forMessage(query3));
        assertFalse(query1Handled.get());
        assertFalse(query2HandledParent.get());
        assertFalse(query2HandledChild.get());
        assertTrue(query3Handled.get());
    }

    @Test
    void supportedQueryReturnsAllSupportedQueries() {
        assertThat(handlingComponent.supportedQueries().size()).isEqualTo(3);
        QueryHandlerName expectedHandlerOne =
                new QueryHandlerName(new QualifiedName("Query1"), new QualifiedName(String.class));
        QueryHandlerName expectedHandlerTwo =
                new QueryHandlerName(new QualifiedName("Query2"), new QualifiedName(String.class));
        QueryHandlerName expectedHandlerThree =
                new QueryHandlerName(new QualifiedName("Query3"), new QualifiedName(String.class));
        assertThat(handlingComponent.supportedQueries())
                .contains(expectedHandlerOne, expectedHandlerTwo, expectedHandlerThree);
    }

    @Test
    void handleWithUnknownPayloadReturnsInFailure() {
        QueryMessage query = new GenericQueryMessage(new MessageType("Query4"), "", RESPONSE_TYPE);
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> handlingComponent.handle(query, StubProcessingContext.forMessage(query))
                                       .first()
                                       .asCompletableFuture()
                                       .join()
        );
        assertInstanceOf(NoHandlerForQueryException.class, exception.getCause());
    }

    @Test
    void handleReturnsFailedMessageStreamForExceptionThrowingQueryHandlingComponent() {
        QualifiedName faultyQuery = new QualifiedName("Error!");
        QueryHandler faultyQueryHandler = (query, context) -> {
            throw new MockException();
        };
        handlingComponent.subscribe(faultyQuery, new QualifiedName(String.class), faultyQueryHandler);

        QueryMessage query = new GenericQueryMessage(new MessageType(faultyQuery), "", RESPONSE_TYPE);
        MessageStream<QueryResponseMessage> result =
                handlingComponent.handle(query, StubProcessingContext.forMessage(query));

        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isPresent();
        assertThat(resultError.get()).isInstanceOf(MockException.class);
    }

    @Test
    void handleReturnsFailedMessageStreamForExceptionThrowingQueryHandler() {
        QualifiedName faultyQuery = new QualifiedName("Error!");
        QueryHandlerName faultyQueryName = new QueryHandlerName(faultyQuery, new QualifiedName(String.class));
        QueryHandlingComponent faultyComponent = mock(QueryHandlingComponent.class);
        when(faultyComponent.handle(any(), any())).thenThrow(new MockException());
        when(faultyComponent.supportedQueries()).thenReturn(Set.of(faultyQueryName));
        handlingComponent.subscribe(faultyComponent);

        QueryMessage query = new GenericQueryMessage(new MessageType(faultyQuery), "", RESPONSE_TYPE);
        MessageStream<QueryResponseMessage> result =
                handlingComponent.handle(query, StubProcessingContext.forMessage(query));

        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isPresent();
        assertThat(resultError.get()).isInstanceOf(MockException.class);
    }
}