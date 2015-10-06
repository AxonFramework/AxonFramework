package org.axonframework.eventhandling;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.async.AsynchronousCluster;
import org.axonframework.eventhandling.async.SequentialPolicy;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

/**
 * Test monitor behavior of Async cluster when UOW rolls back.
 * <p/>
 * See http://issues.axonframework.org/youtrack/issue/AXON-366
 *
 * @author Patrick Haas
 */
public class ClusterEventProcessingMonitorTest {

    private final LoggingMonitor monitor = new LoggingMonitor();
    private final SimpleCluster simpleCluster = new SimpleCluster("SimpleCluster");

    private final EventListener eventListener = new EventListener() {
        @Override
        public void handle(EventMessage event) {
            // System.out.println("Handling: " + event);
            // Throw errors here to test direct failure modes...
        }
    };
    private AsynchronousCluster asynchronousCluster = new AsynchronousCluster("AsynchronousCluster",
            Executors.newSingleThreadExecutor(), new SequentialPolicy());

    @Before
    public void setUp() throws Exception {
        asynchronousCluster.subscribeEventProcessingMonitor(monitor);
        asynchronousCluster.subscribe(eventListener);

        simpleCluster.subscribeEventProcessingMonitor(monitor);
        simpleCluster.subscribe(eventListener);
    }

    @Test
    public void testAsyncCluster_WithoutUnitOfWork() throws Exception {
        publishEventsWithoutUow(asynchronousCluster);
    }

    @Test
    public void testSimpleCluster_WithoutUnitOfWork() throws Exception {
        publishEventsWithoutUow(simpleCluster);
    }

    @Test
    public void testAsyncClusterMonitoring_WithUnitOfWork_Success() throws Exception {
        publishEventsWithUowAndCommit(asynchronousCluster);
    }

    @Test
    public void testSimpleClusterMonitoring_WithUnitOfWork_Success() throws Exception {
        publishEventsWithUowAndCommit(simpleCluster);
    }

    @Test
    public void testAsyncClusterMonitoring_WithUnitOfWork_Failure() throws Exception {
        publishEventsWithUowAndRollback(asynchronousCluster);
    }

    @Test
    public void testSimpleClusterMonitoring_WithUnitOfWork_Failure() throws Exception {
        publishEventsWithUowAndRollback(simpleCluster);
    }

    /**
     * Publish messages without a UnitOfWork. Verify that the monitor is notified of the
     * processing success.
     */
    private void publishEventsWithoutUow(Cluster cluster) {
        cluster.publish(asEventMessage("1"), asEventMessage("2"));
        cluster.publish(asEventMessage("3"));

        monitor.waitForMessages(3, 100);

        assertEquals(3L, count(monitor.completed));
        assertEquals(0L, count(monitor.failed));
    }

    /**
     * Publish messages. Commit the UnitOfWork. Verify that the monitor is notified of the
     * processing success.
     */
    public void publishEventsWithUowAndCommit(Cluster cluster) throws InterruptedException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        cluster.publish(asEventMessage("1"), asEventMessage("2"));
        cluster.publish(asEventMessage("3"));
        uow.commit();

        monitor.waitForMessages(3, 100);

        assertEquals(3L, count(monitor.completed));
        assertEquals(0L, count(monitor.failed));
    }

    /**
     * Publish messages. Roll back the UnitOfWork. Verify that the monitor is notified of the
     * processing failure.
     */
    public void publishEventsWithUowAndRollback(Cluster cluster) throws InterruptedException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        cluster.publish(asEventMessage("1"), asEventMessage("2"));
        cluster.publish(asEventMessage("3"));
        uow.rollback();

        monitor.waitForMessages(3, 100);

        assertEquals(0L, count(monitor.completed));
        assertEquals(3L, count(monitor.failed));
    }

    /**
     * Count the number of individual events contained within the lists in the
     * event message list collection.
     *
     * @param items a collection of published events
     * @return the total number of events in the collection
     */
    private static long count(Collection<List<? extends EventMessage>> items) {
        long count = 0;
        for (List<? extends EventMessage> tx : items) {
            count += tx.size();
        }
        return count;
    }

    /**
     * Monitor event processing for failure and completion of events.
     */
    private static class LoggingMonitor implements EventProcessingMonitor {
        public final Queue<List<? extends EventMessage>> completed = new ConcurrentLinkedQueue<List<? extends EventMessage>>();
        public final Queue<List<? extends EventMessage>> failed = new ConcurrentLinkedQueue<List<? extends EventMessage>>();
        public final AtomicLong count = new AtomicLong();

        @Override
        public void onEventProcessingCompleted(List<? extends EventMessage> eventMessages) {
            completed.add(eventMessages);
            count.addAndGet(eventMessages.size());
        }

        @Override
        public void onEventProcessingFailed(List<? extends EventMessage> eventMessages, Throwable cause) {
            failed.add(eventMessages);
            count.addAndGet(eventMessages.size());
        }

        /**
         * Wait until at least the expected number of events have been received, or the timeout has been reached.
         *
         * @param expectedCount expected number of event notifications to be received by the monitor
         * @param timeout       maximum wait time in milliseconds. Values less than 10 ms have no effect.
         */
        public void waitForMessages(long expectedCount, long timeout) {
            long end = System.currentTimeMillis() + timeout;
            while (System.currentTimeMillis() < end) {
                if (count.get() >= expectedCount) {
                    return;
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public String toString() {
            return String.format("Completed: %3d [%3d]%nFailed:    %3d [%3d]%n",
                    completed.size(), count(completed),
                    failed.size(), count(failed));
        }
    }
}
