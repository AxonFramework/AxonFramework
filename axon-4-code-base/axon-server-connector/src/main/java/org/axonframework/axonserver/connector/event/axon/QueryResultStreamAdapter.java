/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.event.EventQueryResultEntry;

import java.util.concurrent.TimeUnit;

/**
 * Adapter for the {@link QueryResultStream}, wrapping a {@link ResultStream} of {@link EventQueryResultEntry}. Defers
 * calls to the given {@code resultStream}, adapting them to a {@link QueryResult}.
 *
 * @author Allard Buijze
 * @since 4.5.2
 */
public class QueryResultStreamAdapter implements QueryResultStream {

    private final ResultStream<EventQueryResultEntry> resultStream;

    private EventQueryResultEntry peeked;

    /**
     * Construct a {@link QueryResultStreamAdapter} deferring calls to the given {@code resultStream}.
     *
     * @param resultStream the stream of {@link EventQueryResultEntry} to adapt to {@link QueryResult} instances
     */
    public QueryResultStreamAdapter(ResultStream<EventQueryResultEntry> resultStream) {
        this.resultStream = resultStream;
    }

    @Override
    public boolean hasNext(int timeout, TimeUnit unit) {
        if (peeked == null) {
            try {
                peeked = resultStream.nextIfAvailable(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return peeked != null;
    }

    @Override
    public QueryResult next() {
        if (peeked == null) {
            peeked = resultStream.nextIfAvailable();
        }
        if (peeked != null) {
            EventQueryResultEntry next = peeked;
            peeked = null;
            return new QueryResult(next);
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        resultStream.close();
    }
}
