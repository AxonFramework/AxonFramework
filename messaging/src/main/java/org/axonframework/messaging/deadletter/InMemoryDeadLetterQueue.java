/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

/**
 * In memory implementation of the {@link DeadLetterQueue}. Maintains a {@link PriorityQueue} per unique sequence
 * identifier contained in the {@link DeadLetter}.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemoryDeadLetterQueue<T extends Message<?>> implements DeadLetterQueue<T> {

    private final ConcurrentNavigableMap<String, Queue<DeadLetter<T>>> deadLetters = new ConcurrentSkipListMap<>();

    @Override
    public void add(DeadLetter<T> deadLetter) {
        deadLetters.computeIfAbsent(deadLetter.sequenceIdentifier(),
                                    sequenceId -> new PriorityQueue<>(DeadLetter::compare))
                   .add(deadLetter);
    }

    @Override
    public boolean contains(String sequenceIdentifier) {
        return deadLetters.containsKey(sequenceIdentifier);
    }

    @Override
    public boolean isEmpty() {
        return deadLetters.isEmpty();
    }

    @Override
    public Stream<DeadLetter<T>> peek() {
        return isEmpty() ? Stream.empty() : deadLetters.firstEntry()
                                                       .getValue()
                                                       .stream();
    }

    @Override
    public Stream<DeadLetter<T>> poll() {
        return isEmpty() ? Stream.empty() : deadLetters.pollFirstEntry()
                                                       .getValue()
                                                       .stream();
    }
}
