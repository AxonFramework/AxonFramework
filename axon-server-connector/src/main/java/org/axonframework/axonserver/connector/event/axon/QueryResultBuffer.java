/*
 * Copyright (c) 2010-2020. Axon Framework
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
package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.event.QueryEventsResponse;
import io.axoniq.axonserver.grpc.event.RowResponse;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Client-side buffer of messages received from the server. Once consumed from this buffer, the client is notified of a
 * permit being consumed, potentially triggering a permit refresh, if flow control is enabled.
 * <p>
 * This class is intended for internal use. Be cautious.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class QueryResultBuffer implements QueryResultStream {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BlockingQueue<RowResponse> queryResultQueue;

    private QueryResult peekEvent;
    private RuntimeException exception;

    private Consumer<QueryResultBuffer> closeCallback;
    private Consumer<Integer> consumeListener = i -> {
    };
    private List<String> columns;

    /**
     * Constructs a {@link QueryResultBuffer}
     */
    public QueryResultBuffer() {
        queryResultQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public boolean hasNext(int timeout, TimeUnit timeUnit) {
        checkException();
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        try {
            while (peekEvent == null && System.currentTimeMillis() < deadline) {
                waitForData(deadline);
            }
            return peekEvent != null;
        } catch (InterruptedException e) {
            logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void checkException() {
        if (exception != null) {
            RuntimeException runtimeException = exception;
            this.exception = null;
            throw runtimeException;
        }
    }

    private void waitForData(long deadline) throws InterruptedException {
        do {
            RowResponse row = queryResultQueue.poll(
                    Math.min(deadline - System.currentTimeMillis(), 200), TimeUnit.MILLISECONDS
            );
            if (row != null) {
                peekEvent = new QueryResult(row, columns);
            }
            checkException();
        } while (!closed && peekEvent == null && System.currentTimeMillis() < deadline);
    }

    @Override
    public QueryResult next() {
        checkException();
        try {
            consumeListener.accept(1);
            return peekEvent;
        } finally {
            peekEvent = null;
        }
    }

    private volatile boolean closed;

    @Override
    public void close() {
        closed = true;
        if (closeCallback != null) {
            closeCallback.accept(this);
        }
    }

    public void registerCloseListener(Consumer<QueryResultBuffer> closeCallback) {
        this.closeCallback = closeCallback;
    }

    public void registerConsumeListener(Consumer<Integer> consumeListener) {
        this.consumeListener = consumeListener;
    }

    public void push(QueryEventsResponse eventWithToken) {
        if (closed) {
            logger.debug("Received date while closed");
            return;
        }
        switch (eventWithToken.getDataCase()) {
            case COLUMNS:
                this.columns = eventWithToken.getColumns().getColumnList();
                break;
            case ROW:
                try {
                    queryResultQueue.put(eventWithToken.getRow());
                } catch (InterruptedException e) {
                    this.close();
                    Thread.currentThread().interrupt();
                }
                break;
            case FILES_COMPLETED:
            case DATA_NOT_SET:
                break;
        }
    }

    public void fail(EventStoreException exception) {
        this.exception = exception;
    }
}
