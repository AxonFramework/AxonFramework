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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.ErrorCategory;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QueueMessageStream;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FlowControlledResponseSenderTest {

    public static final byte[] PAYLOAD = "test".getBytes(StandardCharsets.UTF_8);
    private QueueMessageStream<QueryResponseMessage> upstream;
    private ReplyChannel<QueryResponse> stubDownstream;
    private FlowControlledResponseSender testSubject;

    private static @NotNull GenericQueryResponseMessage newMessage() {
        return new GenericQueryResponseMessage(new MessageType(String.class), PAYLOAD);
    }

    @BeforeEach
    void setUp() {
        stubDownstream = mock();
        upstream = new QueueMessageStream<>();
        testSubject = new FlowControlledResponseSender("testCase", "test", upstream, stubDownstream);
    }

    @Test
    void shouldSendAllAlreadyAvailableMessagesUntilCompletionOnUnlimitedPermits() {
        for (int i = 0; i < 10; i++) {
            upstream.offer(newMessage(), Context.empty());
        }
        upstream.complete();
        testSubject.request(Long.MAX_VALUE);
        verify(stubDownstream, times(10)).send(any());
        verify(stubDownstream).complete();
    }

    @Test
    void shouldSendMessagesUntilCompletionOnUnlimitedPermitsWhenMessagesBecomeAvailable() {
        testSubject.request(Long.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            upstream.offer(newMessage(), Context.empty());
        }
        upstream.complete();
        verify(stubDownstream, times(10)).send(any());
        verify(stubDownstream).complete();
    }

    @Test
    void shouldNotOverlowOnMoreThanMaxLongRequests() {
        testSubject.request(5);
        testSubject.request(Long.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            upstream.offer(newMessage(), Context.empty());
        }
        upstream.complete();
        verify(stubDownstream, times(10)).send(any());
        verify(stubDownstream).complete();
    }

    @Test
    void shouldHonorTheFlowRequestsWhenEnoughMessagesAreAvailable() {
        for (int i = 0; i < 10; i++) {
            upstream.offer(newMessage(), Context.empty());
        }
        upstream.complete();
        for (int i = 0; i < 10; i++) {
            testSubject.request(1);
            verify(stubDownstream, times(i + 1)).send(any());
        }
        verify(stubDownstream).complete();
    }

    @Test
    void shouldTriggerCloseWhenUpstreamCompletesAfterSendingAllRequestedMessages() {
        for (int i = 0; i < 10; i++) {
            upstream.offer(newMessage(), Context.empty());
        }
        for (int i = 0; i < 10; i++) {
            testSubject.request(1);
            verify(stubDownstream, times(i + 1)).send(any());
        }
        upstream.complete();
        verify(stubDownstream).complete();
    }

    @Test
    void shouldOnlySendMessagesWhenRequested() {
        upstream.offer(newMessage(), Context.empty());
        upstream.offer(newMessage(), Context.empty());

        testSubject.request(1);
        verify(stubDownstream, times(1)).send(any());

        testSubject.request(2);
        verify(stubDownstream, times(2)).send(any());

        // there is 1 outstanding request for this message
        upstream.offer(newMessage(), Context.empty());
        verify(stubDownstream, times(3)).send(any());

        testSubject.request(1);
        // this message won't arrive
        upstream.complete();

        // still 3 messages sent
        verify(stubDownstream, times(3)).send(any());
        verify(stubDownstream).complete();
    }

    @Test
    void shouldSendLastMessageWithErrorWhenUpstreamCompletesExceptionally() {
        upstream.offer(newMessage(), Context.empty());
        upstream.completeExceptionally(new RuntimeException("Custom message"));

        verify(stubDownstream, never()).complete();
        verify(stubDownstream, never()).completeWithError(any());

        // this should also trigger the completion
        testSubject.request(1);

        verify(stubDownstream, times(1)).send(any());
        verify(stubDownstream).sendLast(assertArg(e -> {
            assertThat(e.getErrorMessage().getMessage()).isEqualTo("Custom message");
            assertThat(e.getErrorMessage().getErrorCode()).isEqualTo(ErrorCategory.QUERY_EXECUTION_ERROR.errorCode());
            assertThat(e.getErrorCode()).isEqualTo(ErrorCategory.QUERY_EXECUTION_ERROR.errorCode());
        }));
    }

    @Test
    void cancellingConsumerClosesUpstream() {
        upstream.offer(newMessage(), Context.empty());
        upstream.offer(newMessage(), Context.empty());

        testSubject.request(1);
        testSubject.cancel();

        assertThat(upstream.isClosed()).isTrue();
        assertThat(upstream.offer(newMessage(), Context.empty())).isFalse();
    }
}