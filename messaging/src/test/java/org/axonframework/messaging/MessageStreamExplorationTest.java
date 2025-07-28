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
        AtomicInteger stream1ProcessCount = new AtomicInteger(0);
        AtomicInteger stream2ProcessCount = new AtomicInteger(0);
        
        MessageStream<EventMessage<Integer>> stream1 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(2))
            .onNext(entry -> stream1ProcessCount.incrementAndGet());
        
        MessageStream<EventMessage<Integer>> stream2 = MessageStream.fromIterable(EventTestUtils.<Integer>createEvents(2))
            .onNext(entry -> stream2ProcessCount.incrementAndGet());

        // when
        MessageStream<EventMessage<Integer>> concatenated = stream1.concatWith(stream2);
        
        // then
        List<Integer> results = new ArrayList<>();
        concatenated.asFlux()
                   .map(entry -> entry.message().getPayload())
                   .doOnNext(payload -> {
                       results.add(payload);
                       if (payload == 1 && stream1ProcessCount.get() == 2) {
                           // After first stream completes, second hasn't started yet
                           assertThat(stream2ProcessCount.get()).isEqualTo(0);
                       }
                   })
                   .blockLast();
        
        assertThat(stream1ProcessCount.get()).isEqualTo(2);
        assertThat(stream2ProcessCount.get()).isEqualTo(2);
        assertThat(results).containsExactly(0, 1, 0, 1);
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