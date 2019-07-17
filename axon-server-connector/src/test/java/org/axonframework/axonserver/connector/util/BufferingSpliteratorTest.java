/*
 * Copyright (c) 2010-2019. Axon Framework
 *
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

package org.axonframework.axonserver.connector.util;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class BufferingSpliteratorTest {

    private static final Consumer<Object> IGNORE = s -> {};

    @Test(timeout = 1000)
    public void testTimeout() {
        BufferingSpliterator<String> bufferingSpliterator = new BufferingSpliterator<>(Instant.now().plusMillis(250));
        long t1 = System.currentTimeMillis();
        assertFalse(bufferingSpliterator.tryAdvance(IGNORE));
        long t2 = System.currentTimeMillis();
        assertTrue("Expected at least 150 millis to have elapsed", t2 - t1 > 150);
    }

    @Test(timeout = 10000)
    public void testCompleteWithNull() {
        BufferingSpliterator<String> bufferingSpliterator = new BufferingSpliterator<>(Instant.now().plusSeconds(10));
        Thread queueListener = new Thread(() -> bufferingSpliterator.tryAdvance(IGNORE));
        queueListener.start();
        bufferingSpliterator.cancel(null);
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertFalse(queueListener.isAlive()));
    }

    @Test
    public void testCompleteWithValues() {
        BufferingSpliterator<String> bufferingSpliterator = new BufferingSpliterator<>(Instant.now().plusSeconds(1));
        List<String> items = new CopyOnWriteArrayList<>();
        Thread queueListener = new Thread(new Runnable() {
            @Override
            public void run() {
                StreamSupport.stream(bufferingSpliterator, false).forEach(items::add);
            }
        });
        queueListener.start();
        bufferingSpliterator.put("One");
        bufferingSpliterator.put("Two");
        bufferingSpliterator.cancel(null);

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(2, items.size()));
    }

}
