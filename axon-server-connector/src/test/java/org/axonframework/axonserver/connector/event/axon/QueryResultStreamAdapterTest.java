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
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link QueryResultStreamAdapter}.
 *
 * @author Allard Buijze
 */
class QueryResultStreamAdapterTest {

    private ResultStream<EventQueryResultEntry> stream;

    private QueryResultStreamAdapter testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        stream = mock(ResultStream.class);

        testSubject = new QueryResultStreamAdapter(stream);
    }

    @Test
    void hasNextBuffersNextEntry() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext());
        //noinspection ConstantConditions
        assertTrue(testSubject.hasNext());

        verify(stream, times(1)).nextIfAvailable(1, TimeUnit.SECONDS);
    }

    @Test
    void nextClearsBufferedEntry() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext());
        QueryResult firstResult = testSubject.next();
        assertNotNull(firstResult);

        verify(stream, times(1)).nextIfAvailable(1, TimeUnit.SECONDS);

        assertTrue(testSubject.hasNext());
        QueryResult secondResult = testSubject.next();
        assertNotEquals(firstResult, secondResult); // entries differ, so first entry has been cleared
        verify(stream, times(2)).nextIfAvailable(1, TimeUnit.SECONDS);
    }

    @Test
    void hasNextReturnsFalseAtEnd() throws InterruptedException {
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
    void nextReturnsNullAtEnd() throws InterruptedException {
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
    void hasNextBlocksWithGivenTimeout() throws InterruptedException {
        EventQueryResultEntry mockResult1 = mock(EventQueryResultEntry.class);
        EventQueryResultEntry mockResult2 = mock(EventQueryResultEntry.class);
        doReturn(mockResult1, mockResult2, null)
                .when(stream)
                .nextIfAvailable(anyLong(), any());

        assertTrue(testSubject.hasNext(5, TimeUnit.DAYS));

        verify(stream, times(1)).nextIfAvailable(5, TimeUnit.DAYS);
    }
}