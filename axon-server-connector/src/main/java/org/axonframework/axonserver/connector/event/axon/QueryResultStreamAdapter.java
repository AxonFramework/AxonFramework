package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.event.EventQueryResultEntry;

import java.util.concurrent.TimeUnit;

public class QueryResultStreamAdapter implements QueryResultStream {
    private final ResultStream<EventQueryResultEntry> resultStream;

    private EventQueryResultEntry peeked;

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
