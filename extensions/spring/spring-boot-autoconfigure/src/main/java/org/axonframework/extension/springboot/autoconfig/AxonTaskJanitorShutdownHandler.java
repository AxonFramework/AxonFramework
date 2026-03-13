/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.timeout.AxonTaskJanitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

/**
 * Spring lifecycle handler responsible for shutting down the Axon janitor executor service during application shutdown.
 * This ensures that the non-daemon janitor thread is properly terminated when the Spring context closes.
 *
 * @author Axon Framework Contributors
 * @since 4.13.1
 */
@Internal
public class AxonTaskJanitorShutdownHandler implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private volatile boolean running = false;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        try {
            logger.debug("Shutting down Axon task janitor executor service");
            AxonTaskJanitor.INSTANCE.shutdown();
            
            // Wait for termination with a timeout
            if (!AxonTaskJanitor.INSTANCE.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Axon task janitor executor service did not terminate within the timeout period. " +
                        "Attempting to force shutdown.");
                AxonTaskJanitor.INSTANCE.shutdownNow();
            }
            logger.debug("Axon task janitor executor service has been shut down successfully");
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for Axon task janitor executor service to terminate", e);
            AxonTaskJanitor.INSTANCE.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Run at the very end of the shutdown process (high phase number means it runs last)
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
