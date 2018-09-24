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

package org.axonframework.axonserver.connector.query;

import org.axonframework.axonserver.connector.query.QueueBackedSpliterator;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class QueueBackedSpliteratorTest {

    @Test
    public void testTimeout() {
        QueueBackedSpliterator<String> queueBackedSpliterator = new QueueBackedSpliterator<>(1000, TimeUnit.MILLISECONDS);
        assertFalse(queueBackedSpliterator.tryAdvance(s -> System.out.println(s)));
    }

    @Test
    public void testCompleteWithNull() {
        QueueBackedSpliterator<String> queueBackedSpliterator = new QueueBackedSpliterator<>(1000, TimeUnit.MILLISECONDS);
        Thread queueListener = new Thread(new Runnable() {
            @Override
            public void run() {
                queueBackedSpliterator.tryAdvance(s -> System.out.println(s));

            }
        });
        queueListener.start();
        queueBackedSpliterator.cancel(null);
    }

    @Test
    public void testCompleteWithValues() {
        QueueBackedSpliterator<String> queueBackedSpliterator = new QueueBackedSpliterator<>(1000, TimeUnit.MILLISECONDS);
        Thread queueListener = new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> items = StreamSupport.stream(queueBackedSpliterator, false).collect(Collectors.toList());
                assertEquals(2, items.size());
            }
        });
        queueListener.start();
        queueBackedSpliterator.put("One");
        queueBackedSpliterator.put("Two");
        queueBackedSpliterator.cancel(null);
    }

}