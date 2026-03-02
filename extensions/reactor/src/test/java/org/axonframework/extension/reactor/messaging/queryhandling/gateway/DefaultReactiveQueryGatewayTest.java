/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.reactor.messaging.queryhandling.gateway;

import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link DefaultReactiveQueryGateway}.
 */
class DefaultReactiveQueryGatewayTest {

    private static final String QUERY_PAYLOAD = "query";
    private static final String RESPONSE_PAYLOAD = "answer";

    private QueryGateway mockQueryGateway;

    private DefaultReactiveQueryGateway testSubject;

    @BeforeEach
    void setUp() {
        mockQueryGateway = mock(QueryGateway.class);
        testSubject = DefaultReactiveQueryGateway.builder()
                .queryGateway(mockQueryGateway)
                .messageTypeResolver(new ClassBasedMessageTypeResolver())
                .build();
    }

    @Nested
    class QuerySingle {

        @Test
        void queryInvokesQueryGatewayAndReturnsSingleResult() {
            // given
            when(mockQueryGateway.query(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(CompletableFuture.completedFuture(RESPONSE_PAYLOAD));

            // when / then
            StepVerifier.create(testSubject.query(QUERY_PAYLOAD, String.class))
                        .expectNext(RESPONSE_PAYLOAD)
                        .verifyComplete();

            ArgumentCaptor<QueryMessage> captor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryGateway).query(captor.capture(), eq(String.class));
            assertThat(captor.getValue().payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(captor.getValue().payloadType()).isEqualTo(String.class);
            assertThat(captor.getValue().metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void queryReturningFailedFutureReturnsExceptionalMono() {
            // given
            when(mockQueryGateway.query(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("oops")));

            // when / then
            StepVerifier.create(testSubject.query(QUERY_PAYLOAD, String.class))
                        .expectErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"))
                        .verify();
        }
    }

    @Nested
    class QueryCancellation {

        @Test
        void cancellingQueryMonoCompletesWithoutError() {
            // given
            CompletableFuture<String> neverCompleting = new CompletableFuture<>();
            when(mockQueryGateway.query(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(neverCompleting);

            // when / then
            StepVerifier.create(testSubject.query(QUERY_PAYLOAD, String.class))
                        .thenCancel()
                        .verify();
        }

        @Test
        void cancellingQueryManyMonoCompletesWithoutError() {
            // given
            CompletableFuture<List<String>> neverCompleting = new CompletableFuture<>();
            when(mockQueryGateway.queryMany(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(neverCompleting);

            // when / then
            StepVerifier.create(testSubject.queryMany(QUERY_PAYLOAD, String.class))
                        .thenCancel()
                        .verify();
        }
    }

    @Nested
    class QueryMany {

        @Test
        void queryManyInvokesQueryGatewayAndReturnsListResult() {
            // given
            when(mockQueryGateway.queryMany(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(CompletableFuture.completedFuture(List.of("a", "b")));

            // when / then
            StepVerifier.create(testSubject.queryMany(QUERY_PAYLOAD, String.class))
                        .assertNext(list -> {
                            assertThat(list).hasSize(2);
                            assertThat(list).containsExactly("a", "b");
                        })
                        .verifyComplete();

            ArgumentCaptor<QueryMessage> captor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryGateway).queryMany(captor.capture(), eq(String.class));
            assertThat(captor.getValue().payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(captor.getValue().payloadType()).isEqualTo(String.class);
            assertThat(captor.getValue().metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void queryManyReturningFailedFutureReturnsExceptionalMono() {
            // given
            when(mockQueryGateway.queryMany(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("oops")));

            // when / then
            StepVerifier.create(testSubject.queryMany(QUERY_PAYLOAD, String.class))
                        .expectErrorMatches(t -> t instanceof RuntimeException && t.getMessage().equals("oops"))
                        .verify();
        }
    }

    @Nested
    class StreamingQuery {

        @Test
        void streamingQueryInvokesQueryGatewayAndStreamsResults() {
            // given
            Publisher<String> publisher = Flux.just("x", "y", "z");
            when(mockQueryGateway.streamingQuery(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(publisher);

            // when / then
            StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class))
                        .expectNext("x", "y", "z")
                        .verifyComplete();

            ArgumentCaptor<QueryMessage> captor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryGateway).streamingQuery(captor.capture(), eq(String.class));
            assertThat(captor.getValue().payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(captor.getValue().payloadType()).isEqualTo(String.class);
            assertThat(captor.getValue().metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void streamingQueryPropagatesErrors() {
            // given
            when(mockQueryGateway.streamingQuery(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(Flux.error(new IllegalStateException("test")));

            // when / then
            StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class))
                        .expectErrorMatches(
                                t -> t instanceof IllegalStateException && t.getMessage().equals("test")
                        )
                        .verify();
        }
    }

    @Nested
    class SubscriptionQuery {

        @Test
        void subscriptionQueryInvokesQueryGatewayAndStreamsResults() {
            // given
            Publisher<String> publisher = Flux.just("initial", "update1", "update2");
            when(mockQueryGateway.subscriptionQuery(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(publisher);

            // when / then
            StepVerifier.create(testSubject.subscriptionQuery(QUERY_PAYLOAD, String.class))
                        .expectNext("initial", "update1", "update2")
                        .verifyComplete();

            ArgumentCaptor<QueryMessage> captor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryGateway).subscriptionQuery(captor.capture(), eq(String.class));
            assertThat(captor.getValue().payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(captor.getValue().payloadType()).isEqualTo(String.class);
            assertThat(captor.getValue().metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void subscriptionQueryExceptionReportedInFlux() {
            // given
            when(mockQueryGateway.subscriptionQuery(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(Flux.error(new RuntimeException("fail")));

            // when / then
            StepVerifier.create(testSubject.subscriptionQuery(QUERY_PAYLOAD, String.class))
                        .verifyError(RuntimeException.class);
        }
    }

    @Nested
    class Interceptors {

        @Test
        void interceptorEnrichesMetadataOnQuery() {
            // given
            ReactiveMessageDispatchInterceptor<QueryMessage> interceptor = (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("tenant", "acme"));
                return chain.proceed(enriched);
            };
            testSubject.registerDispatchInterceptor(interceptor);

            when(mockQueryGateway.query(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(CompletableFuture.completedFuture("ok"));

            // when / then
            StepVerifier.create(testSubject.query(QUERY_PAYLOAD, String.class))
                        .expectNext("ok")
                        .verifyComplete();

            ArgumentCaptor<QueryMessage> captor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryGateway).query(captor.capture(), eq(String.class));
            assertThat(captor.getValue().metadata().get("tenant")).isEqualTo("acme");
        }

        @Test
        void interceptorEnrichesMetadataOnStreamingQuery() {
            // given
            ReactiveMessageDispatchInterceptor<QueryMessage> interceptor = (message, chain) -> {
                var enriched = message.andMetadata(Metadata.with("tenant", "acme"));
                return chain.proceed(enriched);
            };
            testSubject.registerDispatchInterceptor(interceptor);

            when(mockQueryGateway.streamingQuery(any(QueryMessage.class), eq(String.class)))
                    .thenReturn(Flux.just("a"));

            // when / then
            StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class))
                        .expectNext("a")
                        .verifyComplete();

            ArgumentCaptor<QueryMessage> captor = ArgumentCaptor.forClass(QueryMessage.class);
            verify(mockQueryGateway).streamingQuery(captor.capture(), eq(String.class));
            assertThat(captor.getValue().metadata().get("tenant")).isEqualTo("acme");
        }

        @Test
        void interceptorCanRejectQuery() {
            // given
            ReactiveMessageDispatchInterceptor<QueryMessage> rejecting = (message, chain) ->
                    reactor.core.publisher.Mono.error(new IllegalArgumentException("rejected"));
            testSubject.registerDispatchInterceptor(rejecting);

            // when / then
            StepVerifier.create(testSubject.query(QUERY_PAYLOAD, String.class))
                        .expectError(IllegalArgumentException.class)
                        .verify();
        }
    }

    @Nested
    class BuilderValidation {

        @Test
        void rejectsNullQueryGateway() {
            assertThatThrownBy(() ->
                    DefaultReactiveQueryGateway.builder().queryGateway(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullMessageTypeResolver() {
            assertThatThrownBy(() ->
                    DefaultReactiveQueryGateway.builder().messageTypeResolver(null)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBuildWithoutQueryGateway() {
            assertThatThrownBy(() ->
                    DefaultReactiveQueryGateway.builder()
                            .messageTypeResolver(new ClassBasedMessageTypeResolver())
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBuildWithoutMessageTypeResolver() {
            assertThatThrownBy(() ->
                    DefaultReactiveQueryGateway.builder()
                            .queryGateway(mockQueryGateway)
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }
    }
}
