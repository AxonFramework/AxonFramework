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

package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.connector.ResultStream;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;

public class StubResultStream<T> implements ResultStream<T> {

    private final Iterator<T> responses;
    private final Throwable error;
    private T peeked;
    private volatile boolean closed;
    private final int totalNumberOfElements;

    public StubResultStream(Throwable error) {
        this.error = error;
        this.closed = true;
        this.responses = Collections.emptyIterator();
        this.totalNumberOfElements = 1;
    }

    @SafeVarargs
    public StubResultStream(T... responses) {
        this.error = null;
        List<T> queryResponses = asList(responses);
        this.responses = queryResponses.iterator();
        this.totalNumberOfElements = queryResponses.size();
        this.closed = totalNumberOfElements == 0;
    }

    @Override
    public T peek() {
        if (peeked == null && responses.hasNext()) {
            peeked = responses.next();
        }
        return peeked;
    }

    @Override
    public T nextIfAvailable() {
        if (peeked != null) {
            T result = peeked;
            peeked = null;
            closeIfThereAreNoMoreElements();
            return result;
        }
        if (responses.hasNext()) {
            T next = responses.next();
            closeIfThereAreNoMoreElements();
            return next;
        } else {
            return null;
        }
    }

    private void closeIfThereAreNoMoreElements() {
        if (!responses.hasNext() && !isClosed()) {
            close();
        }
    }

    @Override
    public T nextIfAvailable(long timeout, TimeUnit unit) {
        return nextIfAvailable();
    }

    @Override
    public T next() {
        return nextIfAvailable();
    }

    @Override
    public void onAvailable(Runnable r) {
        if (peeked != null || responses.hasNext() || isClosed()) {
            IntStream.rangeClosed(0, totalNumberOfElements)
                     .forEach(i -> r.run());
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
}
