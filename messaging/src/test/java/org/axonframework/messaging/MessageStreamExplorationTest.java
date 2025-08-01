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

package org.axonframework.messaging;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageStreamExplorationTest {

    @Test
    void concatWith_combinesStreamsSequentially() {
        // given
        List<EventMessage<Integer>> stream1Events = EventTestUtils.<Integer>createEvents(3);
        List<EventMessage<Integer>> stream2Events = EventTestUtils.<Integer>createEvents(3);
        
        MessageStream<EventMessage<Integer>> stream1 = MessageStream.fromIterable(stream1Events);
        MessageStream<EventMessage<Integer>> stream2 = MessageStream.fromIterable(stream2Events);

        // when
        MessageStream<EventMessage<Integer>> concatenated = stream1.concatWith(stream2);
        
        // then
        List<Integer> results = new ArrayList<>();
        concatenated.asFlux()
                   .map(entry -> entry.message().getPayload())
                   .doOnNext(results::add)
                   .blockLast();
        
        assertThat(results).containsExactly(0, 1, 2, 0, 1, 2);
    }

    @Test
    void concatWith_secondStreamStartsOnlyAfterFirstCompletes() {
        // given
        List<String> processingOrder = new ArrayList<>();
        
        MessageStream<EventMessage<Integer>> stream1 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(2))
            .onNext(entry -> processingOrder.add("stream1-" + entry.message().getPayload()));
        
        MessageStream<EventMessage<Integer>> stream2 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(2))
            .onNext(entry -> processingOrder.add("stream2-" + entry.message().getPayload()));

        // when
        MessageStream<EventMessage<Integer>> concatenated = stream1.concatWith(stream2);
        
        // then
        List<Integer> results = new ArrayList<>();
        concatenated.asFlux()
                   .map(entry -> entry.message().getPayload())
                   .doOnNext(results::add)
                   .blockLast();
        
        assertThat(results).containsExactly(0, 1, 0, 1);
        assertThat(processingOrder).containsExactly("stream1-0", "stream1-1", "stream2-0", "stream2-1");
    }

    // not true, CompletableFuture is run immediately when created
    @Test
    void concatWith_secondAsyncStreamIsLazyAndNotExecutedUntilFirstCompletes() {
        // given
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        AtomicBoolean stream1Started = new AtomicBoolean(false);
        AtomicBoolean stream2Started = new AtomicBoolean(false);
        AtomicBoolean stream1Completed = new AtomicBoolean(false);

        try {
            // First stream completes after 100ms
            CompletableFuture<EventMessage<String>> future1 = CompletableFuture.supplyAsync(() -> {
                stream1Started.set(true);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                stream1Completed.set(true);
                return EventTestUtils.asEventMessage("first");
            }, executor);

            // Second stream should only start executing after first completes
            CompletableFuture<EventMessage<String>> future2 = CompletableFuture.supplyAsync(() -> {
                stream2Started.set(true);
                // This should only be called after stream1 completes
                assertThat(stream1Completed.get()).isTrue();
                return EventTestUtils.asEventMessage("second");
            }, executor);

            MessageStream<EventMessage<String>> stream1 = MessageStream.fromFuture(future1);
            MessageStream<EventMessage<String>> stream2 = MessageStream.fromFuture(future2);

            // when
            MessageStream<EventMessage<String>> concatenated = stream1.concatWith(stream2);

            // Give some time to ensure second stream doesn't start prematurely
            Thread.sleep(50);

            // then - at this point stream1 should have started but stream2 should not
            assertThat(stream1Started.get()).isTrue();
            assertThat(stream2Started.get()).isFalse();

            List<String> results = new ArrayList<>();
            concatenated.asFlux()
                       .map(entry -> entry.message().getPayload())
                       .doOnNext(results::add)
                       .blockLast();

            assertThat(results).containsExactly("first", "second");
            assertThat(stream1Started.get()).isTrue();
            assertThat(stream2Started.get()).isTrue();
            assertThat(stream1Completed.get()).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void concatWith_failsIfFirstStreamFails() {
        // given
        RuntimeException testException = new RuntimeException("Stream 1 failed");
        MessageStream<EventMessage<Integer>> stream1 = MessageStream.failed(testException);
        MessageStream<EventMessage<Integer>> stream2 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(2));

        // when
        MessageStream<EventMessage<Integer>> concatenated = stream1.concatWith(stream2);
        
        // then
        assertThatThrownBy(() -> concatenated.asFlux().blockLast())
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Stream 1 failed");
    }

    @Test
    void whenComplete_executesCallbackOnSuccessfulCompletion() {
        // given
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        MessageStream<EventMessage<Integer>> stream = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(3))
            .whenComplete(() -> callbackExecuted.set(true));
        
        // when
        List<Integer> results = new ArrayList<>();
        stream.asFlux()
              .map(entry -> entry.message().getPayload())
              .doOnNext(payload -> {
                  results.add(payload);
                  processedCount.incrementAndGet();
                  // then - callback should not execute until completion
                  assertThat(callbackExecuted.get()).isFalse();
              })
              .blockLast();
        
        // then
        assertThat(results).containsExactly(0, 1, 2);
        assertThat(processedCount.get()).isEqualTo(3);
        assertThat(callbackExecuted.get()).isTrue();
    }

    @Test
    void whenComplete_doesNotExecuteCallbackOnError() {
        // given
        RuntimeException testException = new RuntimeException("Stream failed");
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);
        
        MessageStream<EventMessage<Integer>> stream = MessageStream.<EventMessage<Integer>>failed(testException)
            .whenComplete(() -> callbackExecuted.set(true));
        
        // when & then
        assertThatThrownBy(() -> stream.asFlux().blockLast())
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Stream failed");
        
        assertThat(callbackExecuted.get()).isFalse();
    }

    @Test
    void whenComplete_doesNotChangeStreamContent() {
        // given
        List<EventMessage<Integer>> originalEvents = EventTestUtils.<Integer>createEvents(3);
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);
        
        MessageStream<EventMessage<Integer>> original = MessageStream.fromIterable(originalEvents);
        MessageStream<EventMessage<Integer>> withCallback = original.whenComplete(() -> 
            callbackExecuted.set(true)
        );
        
        // when
        List<Integer> results = new ArrayList<>();
        withCallback.asFlux()
                   .map(entry -> entry.message().getPayload())
                   .doOnNext(results::add)
                   .blockLast();
        
        // then
        assertThat(results).containsExactly(0, 1, 2);
        assertThat(callbackExecuted.get()).isTrue();
    }

    @Test
    void concatWith_vs_whenComplete_differentPurposes() {
        // given
        AtomicBoolean stream1Completed = new AtomicBoolean(false);
        AtomicBoolean stream2Completed = new AtomicBoolean(false);
        
        MessageStream<EventMessage<Integer>> stream1 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(1))
            .whenComplete(() -> stream1Completed.set(true));
        
        MessageStream<EventMessage<Integer>> stream2 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(1))
            .whenComplete(() -> stream2Completed.set(true));

        // when
        MessageStream<EventMessage<Integer>> concatenated = stream1.concatWith(stream2);
        
        List<Integer> results = new ArrayList<>();
        concatenated.asFlux()
                   .map(entry -> entry.message().getPayload())
                   .doOnNext(results::add)
                   .blockLast();
        
        // then
        assertThat(results).containsExactly(0, 0);
        assertThat(stream1Completed.get()).isTrue();
        assertThat(stream2Completed.get()).isTrue();
    }

    @Test
    void concatWith_preservesOrderAndSequencing() {
        // given
        MessageStream<EventMessage<Integer>> numbers1 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(3));
        MessageStream<EventMessage<Integer>> numbers2 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(3));

        // when
        MessageStream<EventMessage<Integer>> concatenated = numbers1.concatWith(numbers2);
        
        List<Integer> results = new ArrayList<>();
        concatenated.asFlux()
                   .map(entry -> entry.message().getPayload())
                   .doOnNext(results::add)
                   .blockLast();
        
        // then
        assertThat(results).containsExactly(0, 1, 2, 0, 1, 2);
        
        // Verify first stream completes before second starts
        for (int i = 0; i < 3; i++) {
            assertThat(results.get(i)).isEqualTo(i);
        }
        for (int i = 3; i < 6; i++) {
            assertThat(results.get(i)).isEqualTo(i - 3);
        }
    }

    @Test
    void whenComplete_canBeChainedMultipleTimes() {
        // given
        AtomicInteger callbackCount = new AtomicInteger(0);
        
        MessageStream<EventMessage<Integer>> stream = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(1))
            .whenComplete(callbackCount::incrementAndGet)
            .whenComplete(callbackCount::incrementAndGet)
            .whenComplete(callbackCount::incrementAndGet);
        
        // when
        stream.asFlux().blockLast();
        
        // then
        assertThat(callbackCount.get()).isEqualTo(3);
    }
}