package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.event.EventQueryResultEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryResultStreamAdapterTest {

    private ResultStream<EventQueryResultEntry> stream;
    private QueryResultStreamAdapter testSubject;

    @BeforeEach
    void setUp() {
        stream = mock(ResultStream.class);
        testSubject = new QueryResultStreamAdapter(stream);
    }

    @Test
    void testHasNextBuffersNextEntry() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext());
        assertTrue(testSubject.hasNext());

        verify(stream, times(1)).nextIfAvailable(1, TimeUnit.SECONDS);
    }

    @Test
    void testNextClearsBufferedEntry() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext());
        assertNotNull(testSubject.next());

        verify(stream, times(1)).nextIfAvailable(1, TimeUnit.SECONDS);

        assertTrue(testSubject.hasNext());
        verify(stream, times(2)).nextIfAvailable(1, TimeUnit.SECONDS);
    }

    @Test
    void testHasNextReturnsFalseAtEnd() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext());
        assertNotNull(testSubject.next());
        assertTrue(testSubject.hasNext());
        assertNotNull(testSubject.next());

        verify(stream, times(2)).nextIfAvailable(1, TimeUnit.SECONDS);

        assertFalse(testSubject.hasNext());
        verify(stream, times(3)).nextIfAvailable(1, TimeUnit.SECONDS);
    }

    @Test
    void testNextReturnsNullAtEnd() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext());
        assertNotNull(testSubject.next());
        assertTrue(testSubject.hasNext());
        assertNotNull(testSubject.next());

        verify(stream, times(2)).nextIfAvailable(1, TimeUnit.SECONDS);

        assertNull(testSubject.next());
        verify(stream, times(2)).nextIfAvailable(1, TimeUnit.SECONDS);
        verify(stream, times(1)).nextIfAvailable();
    }


    @Test
    void testHasNextBlocksWithGivenTimeout() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext(5, TimeUnit.DAYS));

        verify(stream, times(1)).nextIfAvailable(5, TimeUnit.DAYS);
    }

}