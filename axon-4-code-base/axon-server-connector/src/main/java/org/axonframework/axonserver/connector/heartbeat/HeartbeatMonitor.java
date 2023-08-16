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

package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.util.Scheduler;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Verifies if the connection is still alive, and react if it is not.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class HeartbeatMonitor implements Lifecycle {

    private static final long DEFAULT_INITIAL_DELAY = 10_000;
    private static final long DEFAULT_DELAY = 1_000;
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatMonitor.class);
    private final Scheduler scheduler;
    private final Runnable onInvalidConnection;

    private final ConnectionSanityChecker connectionSanityCheck;
    private final long initialDelay;
    private final long delay;


    /**
     * Primary constructor of {@link HeartbeatMonitor}.
     *
     * @param onInvalidConnection   callback to be call when the connection is no longer alive
     * @param connectionSanityCheck sanity check which allows to verify if the connection is alive
     * @param scheduler             the {@link Scheduler} to use for scheduling the task
     * @param initialDelay          the initial delay, in milliseconds
     * @param delay                 the scheduling period, in milliseconds
     */
    public HeartbeatMonitor(Runnable onInvalidConnection, ConnectionSanityChecker connectionSanityCheck,
                            Scheduler scheduler, long initialDelay, long delay) {
        this.onInvalidConnection = onInvalidConnection;
        this.connectionSanityCheck = connectionSanityCheck;
        this.scheduler = scheduler;
        this.initialDelay = initialDelay;
        this.delay = delay;
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INSTRUCTION_COMPONENTS, this::start);
        lifecycle.onShutdown(Phase.INSTRUCTION_COMPONENTS, this::shutdown);
    }

    /**
     * Verify if the connection with AxonServer is still alive. If it is not, invoke a callback in order to react to the
     * disconnection.
     */
    private void run() {
        try {
            boolean valid = connectionSanityCheck.isValid();
            if (!valid) {
                onInvalidConnection.run();
            }
        } catch (Exception e) {
            logger.warn("Impossible to correctly monitor the Axon Server connection state.", e);
        }
    }

    /**
     * Schedule a task that verifies that the connection is still alive and, if it is not, invoke a callback in order to
     * react to the disconnection. Started in phase {@link Phase#INSTRUCTION_COMPONENTS}, as this means all inbound and
     * outbound connections have been started.
     */
    public void start() {
        this.scheduler.scheduleWithFixedDelay(this::run, initialDelay, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the scheduled task and shutdown the monitor, that cannot be restarted again. Shuts down in phase {@link
     * Phase#INSTRUCTION_COMPONENTS}.
     */
    public void shutdown() {
        this.scheduler.shutdownNow();
    }

    private static final class DefaultScheduler implements Scheduler {

        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        @Override
        public ScheduledTask scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            ScheduledFuture<?> scheduled = executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
            return scheduled::cancel;
        }

        @Override
        public void shutdownNow() {
            executor.shutdown();
        }
    }
}
