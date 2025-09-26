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

import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MessageTypeNotResolvedException;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.ConversionException;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleQueryUpdateEmitter}.
 *
 * @author Allard Buijze
 * @author Corrado Musumeci
 * @author Steven van Beelen
 */
class SimpleQueryUpdateEmitterTest {

    private static final MessageType QUERY_TYPE = new MessageType("query-type");
    private static final MessageType UPDATE_TYPE = new MessageType("update-type");
    private static final Exception CAUSE = new MockException("oops");

    private QueryBus queryBus = QueryBusTestUtils.aQueryBus();
    private MessageTypeResolver messageTypeResolver;
    private MessageConverter converter;
    private ProcessingContext context;

    private SimpleQueryUpdateEmitter testSubject;

    private ArgumentCaptor<Predicate<SubscriptionQueryMessage>> filterCaptor;
    private ArgumentCaptor<Supplier<SubscriptionQueryUpdateMessage>> updateCaptor;

    @BeforeEach
    void setUp() {
        queryBus = mock(QueryBus.class);
        messageTypeResolver = mock(MessageTypeResolver.class);
        converter = mock(MessageConverter.class);
        context = new StubProcessingContext();

        testSubject = new SimpleQueryUpdateEmitter(queryBus, messageTypeResolver, converter, context);

        when(queryBus.emitUpdate(any(), any(), eq(context))).thenReturn(FutureUtils.emptyCompletedFuture());
        when(queryBus.completeSubscriptions(any(), eq(context))).thenReturn(FutureUtils.emptyCompletedFuture());
        when(queryBus.completeSubscriptionsExceptionally(any(), any(), eq(context)))
                .thenReturn(FutureUtils.emptyCompletedFuture());
        filterCaptor = ArgumentCaptor.captor();
        updateCaptor = ArgumentCaptor.captor();
    }

    @Nested
    class Construction {

        @Test
        void throwsNullPointerExceptionForNullQueryBus() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new SimpleQueryUpdateEmitter(
                    null, messageTypeResolver, converter, context
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        void throwsNullPointerExceptionForNullMessageTypeResolver() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new SimpleQueryUpdateEmitter(
                    queryBus, null, converter, context
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        void throwsNullPointerExceptionForNullMessageConverter() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new SimpleQueryUpdateEmitter(
                    queryBus, messageTypeResolver, null, context
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        void throwsNullPointerExceptionForNullApplicationContext() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new SimpleQueryUpdateEmitter(
                    queryBus, messageTypeResolver, converter, null
            )).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class EmittingUpdates {

        @Test
        void emitForQueryType() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, "some-query", instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");
            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            // when...
            testSubject.emit(String.class, query -> true, testUpdatePayload);
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), updateCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            Supplier<SubscriptionQueryUpdateMessage> resultUpdateSupplier = updateCaptor.getValue();
            SubscriptionQueryUpdateMessage resultUpdate = resultUpdateSupplier.get();
            assertThat(resultUpdate.type()).isEqualTo(UPDATE_TYPE);
            assertThat(resultUpdate.payload()).isEqualTo(testUpdatePayload);
            verify(messageTypeResolver).resolveOrThrow(testUpdatePayload);
            verifyNoInteractions(converter);
        }

        @Test
        void emitForQueryTypeAndFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenReturn("some-query");
            // when...
            testSubject.emit(String.class, query -> query.equals("some-query"), testUpdatePayload);
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), updateCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            Supplier<SubscriptionQueryUpdateMessage> resultUpdateSupplier = updateCaptor.getValue();
            SubscriptionQueryUpdateMessage resultUpdate = resultUpdateSupplier.get();
            assertThat(resultUpdate.type()).isEqualTo(UPDATE_TYPE);
            assertThat(resultUpdate.payload()).isEqualTo(testUpdatePayload);

            verify(messageTypeResolver).resolveOrThrow(String.class);
            verify(messageTypeResolver).resolveOrThrow(testUpdatePayload);
            verify(converter).convert(testQueryPayload, (Type) String.class);
        }

        @Test
        void emitForQueryTypeThrowsMessageTypeNotResolvedExceptionDuringFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");

            when(messageTypeResolver.resolveOrThrow(any()))
                    .thenThrow(MessageTypeNotResolvedException.class);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenReturn("some-query");
            // when...
            testSubject.emit(String.class, query -> query.equals("some-query"), testUpdatePayload);
            // then...
            //noinspection unchecked
            verify(queryBus).emitUpdate(filterCaptor.capture(), any(Supplier.class), eq(context));
            assertThatThrownBy(() -> filterCaptor.getValue().test(testQuery))
                    .isInstanceOf(MessageTypeNotResolvedException.class);
        }

        @Test
        void emitForQueryTypeThrowsConversionExceptionDuringFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenThrow(ConversionException.class);
            // when...
            testSubject.emit(String.class, query -> query.equals("some-query"), testUpdatePayload);
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), any(), eq(context));
            assertThatThrownBy(() -> filterCaptor.getValue().test(testQuery))
                    .isInstanceOf(ConversionException.class);
        }

        @Test
        void emitForQueryTypeDoesNotRetrieveUpdateWhenNoQueriesMatch() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            AtomicBoolean updateSupplierInvoked = new AtomicBoolean(false);
            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            // when...
            testSubject.emit(String.class, query -> false, () -> {
                updateSupplierInvoked.set(true);
                return "this should never be returned!";
            });
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), updateCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isFalse();
            assertThat(updateSupplierInvoked).isFalse();
        }

        @Test
        void emitForQueryName() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, "some-query", instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            // when...
            testSubject.emit(QUERY_TYPE.qualifiedName(), query -> true, testUpdatePayload);
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), updateCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            Supplier<SubscriptionQueryUpdateMessage> resultUpdateSupplier = updateCaptor.getValue();
            SubscriptionQueryUpdateMessage resultUpdate = resultUpdateSupplier.get();
            assertThat(resultUpdate.type()).isEqualTo(UPDATE_TYPE);
            assertThat(resultUpdate.payload()).isEqualTo(testUpdatePayload);
            verify(messageTypeResolver).resolveOrThrow(testUpdatePayload);
            verifyNoInteractions(converter);
        }

        @Test
        void emitForQueryNameAndGivenFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            // when...
            testSubject.emit(QUERY_TYPE.qualifiedName(), query -> query.equals(testQueryPayload), testUpdatePayload);
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), updateCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            Supplier<SubscriptionQueryUpdateMessage> resultUpdateSupplier = updateCaptor.getValue();
            SubscriptionQueryUpdateMessage resultUpdate = resultUpdateSupplier.get();
            assertThat(resultUpdate.type()).isEqualTo(UPDATE_TYPE);
            assertThat(resultUpdate.payload()).isEqualTo(testUpdatePayload);

            verify(messageTypeResolver).resolveOrThrow(testUpdatePayload);
            verifyNoInteractions(converter);
        }

        @Test
        void emitForQueryNameDoesNotRetrieveUpdateWhenNoQueriesMatch() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            AtomicBoolean updateSupplierInvoked = new AtomicBoolean(false);
            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            // when...
            testSubject.emit(QUERY_TYPE.qualifiedName(), query -> false, () -> {
                updateSupplierInvoked.set(true);
                return "this should never be returned!";
            });
            // then...
            verify(queryBus).emitUpdate(filterCaptor.capture(), updateCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isFalse();
            assertThat(updateSupplierInvoked).isFalse();

            verify(messageTypeResolver, times(0)).resolveOrThrow(String.class);
            verifyNoInteractions(converter);
        }
    }

    @Nested
    class Complete {

        @Test
        void completeForQueryType() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, "some-query", instanceOf(String.class), instanceOf(String.class)
            );
            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            // when...
            testSubject.complete(String.class, query -> true);
            // then...
            verify(queryBus).completeSubscriptions(filterCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            verify(messageTypeResolver).resolveOrThrow(String.class);
            verifyNoInteractions(converter);
        }

        @Test
        void completeForQueryTypeAndFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenReturn("some-query");
            // when...
            testSubject.complete(String.class, query -> query.equals("some-query"));
            // then...
            verify(queryBus).completeSubscriptions(filterCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            verify(messageTypeResolver).resolveOrThrow(String.class);
            verify(converter).convert(testQueryPayload, (Type) String.class);
        }

        @Test
        void completeForQueryTypeThrowsMessageTypeNotResolvedExceptionDuringFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );

            when(messageTypeResolver.resolveOrThrow(any()))
                    .thenThrow(MessageTypeNotResolvedException.class);
            when(converter.convert(any(), (Type) eq(String.class))).thenReturn("some-query");
            // when...
            testSubject.complete(String.class, query -> query.equals("some-query"));
            // then...
            verify(queryBus).completeSubscriptions(filterCaptor.capture(), eq(context));
            assertThatThrownBy(() -> filterCaptor.getValue().test(testQuery))
                    .isInstanceOf(MessageTypeNotResolvedException.class);
        }

        @Test
        void completeForQueryTypeThrowsConversionExceptionDuringFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenThrow(ConversionException.class);
            // when...
            testSubject.complete(String.class, query -> query.equals("some-query"));
            // then...
            verify(queryBus).completeSubscriptions(filterCaptor.capture(), eq(context));
            assertThatThrownBy(() -> filterCaptor.getValue().test(testQuery))
                    .isInstanceOf(ConversionException.class);
        }

        @Test
        void completeForQueryName() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, "some-query", instanceOf(String.class), instanceOf(String.class)
            );
            // when...
            testSubject.complete(QUERY_TYPE.qualifiedName(), query -> true);
            // then...
            verify(queryBus).completeSubscriptions(filterCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();

            verifyNoInteractions(messageTypeResolver);
            verifyNoInteractions(converter);
        }

        @Test
        void completeForQueryNameAndGivenFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            // when...
            testSubject.complete(QUERY_TYPE.qualifiedName(), query -> query.equals(testQueryPayload));
            // then...
            verify(queryBus).completeSubscriptions(filterCaptor.capture(), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();

            verifyNoInteractions(messageTypeResolver);
            verifyNoInteractions(converter);
        }
    }

    @Nested
    class CompleteExceptionally {

        @Test
        void completeExceptionallyForQueryType() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, "some-query", instanceOf(String.class), instanceOf(String.class)
            );
            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            // when...
            testSubject.completeExceptionally(String.class, query -> true, CAUSE);
            // then...
            verify(queryBus).completeSubscriptionsExceptionally(filterCaptor.capture(), eq(CAUSE), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            verify(messageTypeResolver).resolveOrThrow(String.class);
            verifyNoInteractions(converter);
        }

        @Test
        void completeExceptionallyForQueryTypeAndFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenReturn("some-query");
            // when...
            testSubject.completeExceptionally(String.class, query -> query.equals("some-query"), CAUSE);
            // then...
            verify(queryBus).completeSubscriptionsExceptionally(filterCaptor.capture(), eq(CAUSE), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();
            verify(messageTypeResolver).resolveOrThrow(String.class);
            verify(converter).convert(testQueryPayload, (Type) String.class);
        }

        @Test
        void completeExceptionallyForQueryTypeThrowsMessageTypeNotResolvedExceptionDuringFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );

            when(messageTypeResolver.resolveOrThrow(any()))
                    .thenThrow(MessageTypeNotResolvedException.class);
            when(converter.convert(any(), (Type) eq(String.class))).thenReturn("some-query");
            // when...
            testSubject.completeExceptionally(String.class, query -> query.equals("some-query"), CAUSE);
            // then...
            verify(queryBus).completeSubscriptionsExceptionally(filterCaptor.capture(), eq(CAUSE), eq(context));
            assertThatThrownBy(() -> filterCaptor.getValue().test(testQuery))
                    .isInstanceOf(MessageTypeNotResolvedException.class);
        }

        @Test
        void completeExceptionallyForQueryTypeThrowsConversionExceptionDuringFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(converter.convert(any(), (Type) eq(String.class))).thenThrow(ConversionException.class);
            // when...
            testSubject.completeExceptionally(String.class, query -> query.equals("some-query"), CAUSE);
            // then...
            verify(queryBus).completeSubscriptionsExceptionally(filterCaptor.capture(), eq(CAUSE), eq(context));
            assertThatThrownBy(() -> filterCaptor.getValue().test(testQuery))
                    .isInstanceOf(ConversionException.class);
        }

        @Test
        void completeExceptionallyForQueryName() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, "some-query", instanceOf(String.class), instanceOf(String.class)
            );
            // when...
            testSubject.completeExceptionally(QUERY_TYPE.qualifiedName(), query -> true, CAUSE);
            // then...
            verify(queryBus).completeSubscriptionsExceptionally(filterCaptor.capture(), eq(CAUSE), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();

            verifyNoInteractions(messageTypeResolver);
            verifyNoInteractions(converter);
        }

        @Test
        void completeExceptionallyForQueryNameAndGivenFilter() {
            // given...
            SubscriptionQuery testQueryPayload = new SubscriptionQuery("some-query");
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, testQueryPayload, instanceOf(String.class), instanceOf(String.class)
            );
            Update testUpdatePayload = new Update("some-update");

            when(messageTypeResolver.resolveOrThrow(String.class)).thenReturn(QUERY_TYPE);
            when(messageTypeResolver.resolveOrThrow(testUpdatePayload)).thenReturn(UPDATE_TYPE);
            // when...
            testSubject.completeExceptionally(QUERY_TYPE.qualifiedName(),
                                              query -> query.equals(testQueryPayload),
                                              CAUSE);
            // then...
            verify(queryBus).completeSubscriptionsExceptionally(filterCaptor.capture(), eq(CAUSE), eq(context));
            assertThat(filterCaptor.getValue().test(testQuery)).isTrue();

            verifyNoInteractions(messageTypeResolver);
            verifyNoInteractions(converter);
        }
    }

    private record SubscriptionQuery(String value) {

    }

    private record Update(String value) {

    }
}
