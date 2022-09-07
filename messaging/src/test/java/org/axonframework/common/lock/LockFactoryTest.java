/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.common.lock;

import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that discovers <a href="https://github.com/AxonFramework/AxonFramework/issues/32">issue #32</a>.
 */
class LockFactoryTest {

    private static final int THREAD_COUNT = 4;
    private static final int ATTEMPTS = 3000;

    private LockFactory lockFactory;
    private String aggregateIdentifier;

    @BeforeEach
    void setup() {
        lockFactory = PessimisticLockFactory.builder().build();
        aggregateIdentifier = UUID.randomUUID().toString();
    }

    @Test
    void obtainLock() {
        ExecutorService service = Executors.newFixedThreadPool(THREAD_COUNT);
        LockUnlock[] attempts = new LockUnlock[ATTEMPTS];
        for (int t = 0; t < ATTEMPTS; t++) {
            attempts[t] = new LockUnlock(t);
        }

        long startTime = System.currentTimeMillis();

        for (LockUnlock attempt : attempts) {
            service.submit(attempt);
        }

        service.shutdown();
        try {
            service.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted");
        }

        int failedAttempts = 0;
        for (LockUnlock attempt : attempts) {
            if (!attempt.success) {
                failedAttempts++;
            }
        }
        assertEquals(0, failedAttempts, "Failed LockUnlock count");
    }


    private class LockUnlock implements Runnable {

        private int instanceIndex;
        private boolean success;

        LockUnlock(int instanceIndex) {
            this.instanceIndex = instanceIndex;
        }

        @Override
        public void run() {
            int locksAcquired = 0;
            int locksReleased = 0;
            try {
                Lock lock = lockFactory.obtainLock(aggregateIdentifier);
                locksAcquired++;
                lock.release();
                locksReleased++;

                success = true;
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                sw.append("Failed ")
                  .append(Integer.toString(instanceIndex))
                  .append(" aquired=")
                  .append(Integer.toString(locksAcquired))
                  .append(" release=")
                  .append(Integer.toString(locksReleased))
                  .append(" Exception:");
                PrintWriter writer = new PrintWriter(sw);
                e.printStackTrace(writer);
                System.out.println(sw.toString());
            }
        }
    }
}
