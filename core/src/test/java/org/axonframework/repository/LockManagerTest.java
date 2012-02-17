package org.axonframework.repository;

import org.junit.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test that discovers <a href="https://github.com/AxonFramework/AxonFramework/issues/32">issue #32</a>.
 */
public class LockManagerTest {

    private static final int THREAD_COUNT = 4;
    private static final int ATTEMPTS = 3000;

    private LockManager lockManager;
    private UUID aggregateIdentifier;

    @Before
    public void setup() {
        lockManager = new PessimisticLockManager();
        aggregateIdentifier = UUID.randomUUID();
    }

    @Test
    public void testObtainLock() {
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
            Assert.fail("Interrupted");
        }

        int failedAttempts = 0;
        for (LockUnlock attempt : attempts) {
            if (!attempt.success) {
                failedAttempts++;
            }
        }
        Assert.assertEquals("Failed LockUnlock count", 0, failedAttempts);
        System.out.println("Completed in " + (System.currentTimeMillis() - startTime) + "ms");
    }


    private class LockUnlock implements Runnable {

        private int instanceIndex;
        private boolean success;

        public LockUnlock(int instanceIndex) {
            this.instanceIndex = instanceIndex;
        }

        @Override
        public void run() {
            int locksAquired = 0;
            int locksReleased = 0;
            try {
                lockManager.obtainLock(aggregateIdentifier);
                locksAquired++;
                lockManager.releaseLock(aggregateIdentifier);
                locksReleased++;

                success = true;
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                sw.append("Failed " + instanceIndex + " aquired=" + locksAquired + " release=" + locksReleased
                                  + " Exception:");
                PrintWriter writer = new PrintWriter(sw);
                e.printStackTrace(writer);
                System.out.println(sw.toString());
            }
        }
    }
}