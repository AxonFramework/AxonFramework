/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga.repository;

import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 *
 */
public class SagaCacheTest {

    private SagaCache testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new SagaCache();
    }

    @Test
    public void testSagaCacheIsPurged_WeakReferences() throws Throwable {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        List<Thread> threads = new ArrayList<Thread>();
        final int threadCount = Runtime.getRuntime().availableProcessors();
        final long itemsPerThread = 10000;
        for (int c = 0; c < threadCount; c++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int t1 = 0; t1 < itemsPerThread; t1++) {
                        testSubject.put(new SimpleSaga());
                    }
                }
            });
            threads.add(t);
            t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    exception.set(e);
                }
            });
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        // just a last effort, although it is not a guarantee.
        System.gc();
        // we make sure at least one item has been cleaned up
        testSubject.purge();
        assertTrue(testSubject.size() < 10000);
        Throwable caughtException = exception.get();
        if (caughtException != null) {
            throw caughtException;
        }
    }

    @Test
    public void testUnknownSagaReturnsNull() {
        assertNull(testSubject.get(UUID.randomUUID().toString()));
    }

    public static class SimpleSaga extends AbstractAnnotatedSaga {

    }
}
