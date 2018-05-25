/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling.benchmark;

import org.axonframework.kafka.eventhandling.consumer.KafkaEventMessage;
import org.axonframework.kafka.eventhandling.consumer.SortedKafkaMessageBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 * This compares the speed of operations that can be performed on the bundled {@link SortedKafkaMessageBuffer}. Re-run this
 * benchmark when changing internals of {@link SortedKafkaMessageBuffer}.
 * <p>The {@link SortedKafkaMessageBuffer bundled buffer} aims to be efficient in terms of operations performed on the buffer. It
 * may not always be fastest, but we should try to keep it competitive.
 *
 * @author Nakul Mishra
 */

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class MessageBufferBenchmarks_PutPeekTake {

    @Param(value = "1000000")
    private static int bufferSize;

    private static KafkaEventMessage[] testData;

    private SortedKafkaMessageBuffer<KafkaEventMessage> buffer;

    @Setup(Level.Trial)
    public void createBuffer() {
        buffer = new SortedKafkaMessageBuffer<>(bufferSize);
    }

    @Setup(Level.Trial)
    public void prepareTestData() {
        testData = new KafkaEventMessage[bufferSize];
        int i = 0;
        while (i < bufferSize) {
            testData[i++] = message(0, i, i + 1);
            testData[i++] = message(1, i, i + 1);
            testData[i++] = message(2, i, i + 1);
            testData[i++] = message(3, i, i + 1);
        }
    }

    private static KafkaEventMessage message(int partition, int offset, int timestamp) {
        return new KafkaEventMessage(
                asTrackedEventMessage(asEventMessage(
                        String.valueOf(offset) + "abc" + String.valueOf(offset * 17 + 123)), null),
                partition,
                offset,
                timestamp
        );
    }

    @Benchmark
    @Group("rw")
    public void put(ThreadState state) throws InterruptedException {
        KafkaEventMessage message = testData[state.next() & (testData.length - 1)];
        buffer.put(message);
    }

    @Benchmark
    @Group("rw")
    public KafkaEventMessage peek() {
        return buffer.peek();
    }

    @Benchmark
    @Group("rw")
    public KafkaEventMessage take() throws InterruptedException {
        return buffer.take();
    }

    @State(Scope.Thread)
    public static class ThreadState {

        private SimpleRandom random = new SimpleRandom();

        int next() {
            return random.next();
        }
    }

    // Convenience main entry-point
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + MessageBufferBenchmarks_PutPeekTake.class.getSimpleName() + ".*")
                .threads(40)
                .build();

        new Runner(opt).run();
    }
}
