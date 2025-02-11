package org.axonframework.messaging.timeout;

import org.axonframework.common.AxonThreadFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Unique instance of the ScheduledExecutorService used for scheduling interrupting tasks
 * for the {@link AxonTimeLimitedTask}.
 *
 * @author Mitchell Herrijgers
 * @see AxonTimeLimitedTask
 * @since 4.11.0
 */
public class AxonTaskJanitor {

    public static final ScheduledExecutorService INSTANCE = createJanitorExecutorService();

    private AxonTaskJanitor() {
        // Utility class
    }


    /**
     * Creates the ScheduledExecutorService used for scheduling the interrupting task. It only has one thread as the
     * load is very low. Cancelling the tasks will clean it up to reduce memory pressure.
     *
     * @return The ScheduledExecutorService
     */
    private static ScheduledThreadPoolExecutor createJanitorExecutorService() {
        ScheduledThreadPoolExecutor housekeeper = new ScheduledThreadPoolExecutor(1,
                                                                                  new AxonThreadFactory("axon-janitor"));
        // Clean up tasks in the queue when canceled. Performance is equal but reduces memory pressure.
        housekeeper.setRemoveOnCancelPolicy(true);
        return housekeeper;
    }
}
