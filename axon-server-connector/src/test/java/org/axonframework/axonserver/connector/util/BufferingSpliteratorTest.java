/*
 * Copyright (c) 2018. AxonIQ
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Author: marc
 */
public class BufferingSpliteratorTest {

    @Test
    public void testTimeout() {
        BufferingSpliterator<String> bufferingSpliterator = new BufferingSpliterator<>(Instant.now().plusSeconds(1));
        assertFalse(bufferingSpliterator.tryAdvance(s -> System.out.println(s)));
    }

    @Test
    public void testCompleteWithNull() {
        BufferingSpliterator<String> bufferingSpliterator = new BufferingSpliterator<>(Instant.now().plusSeconds(1));
        Thread queueListener = new Thread(new Runnable() {
            @Override
            public void run() {
                bufferingSpliterator.tryAdvance(s -> System.out.println(s));

            }
        });
        queueListener.start();
        bufferingSpliterator.cancel(null);
    }

    @Test
    public void testCompleteWithValues() {
        BufferingSpliterator<String> bufferingSpliterator = new BufferingSpliterator<>(Instant.now().plusSeconds(1));
        Thread queueListener = new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> items = StreamSupport.stream(bufferingSpliterator, false).collect(Collectors.toList());
                assertEquals(2, items.size());
            }
        });
        queueListener.start();
        bufferingSpliterator.put("One");
        bufferingSpliterator.put("Two");
        bufferingSpliterator.cancel(null);
    }

}
