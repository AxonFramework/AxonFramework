package org.axonframework.disruptor.commandhandling;

import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.IntStream;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class FirstLevelCacheTest {

    private EventSourcedAggregate<MyAggregate> cacheable;
    private FirstLevelCache<MyAggregate> cache;

    @Before
    public void setUp() {
        cacheable = mock(EventSourcedAggregate.class);
        cache = new FirstLevelCache<>();
    }

    @Test
    public void shouldPut() {
        cache.put("key", cacheable);

        assertEquals(cache.size(), 1);
    }

    @Test
    public void shouldGet() {
        cache.put("key", cacheable);
        EventSourcedAggregate<MyAggregate> cached = cache.get("key");

        assertSame(cached, cacheable);
    }

    @Test
    public void shouldRemove() {
        cache.put("key", cacheable);
        EventSourcedAggregate<MyAggregate> cached = cache.remove("key");

        assertSame(cached, cacheable);
        assertEquals(0, cache.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldClearWeakValues() throws Exception {

        FirstLevelCache<FirstLevelCacheTest.MyAggregate> myCache = new FirstLevelCache<>();

        int numberOfEntries = 200;
        IntStream.range(0, numberOfEntries)
            .mapToObj(i -> "key-" + i)
            .forEach(key -> myCache.put(key, mock(EventSourcedAggregate.class)));

        int i = 0;
        while (i < 10 && myCache.size() > 0) {
            System.gc();
            sleep(50);
            i++;
        }
        assertEquals(0, myCache.size());
    }

    static class MyAggregate {

    }
}