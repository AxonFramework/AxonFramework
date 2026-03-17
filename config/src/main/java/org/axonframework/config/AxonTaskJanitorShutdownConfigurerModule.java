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

package org.axonframework.config;

import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.timeout.AxonTaskJanitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * {@link ConfigurerModule} that registers a shutdown hook to shut down the {@link AxonTaskJanitor} executor service
 * when the Axon {@link Configuration} is shut down. This ensures the non-daemon janitor thread is terminated in all
 * environments (Spring and non-Spring), allowing the JVM to exit gracefully.
 * <p>
 * This module is automatically loaded via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/org.axonframework.config.ConfigurerModule} and does not require any user configuration.
 *
 * @author Axon Framework Contributors
 * @see AxonTaskJanitor
 * @since 4.13.1
 */
public class AxonTaskJanitorShutdownConfigurerModule implements ConfigurerModule {

    private static final Logger logger = LoggerFactory.getLogger(AxonTaskJanitorShutdownConfigurerModule.class);

    private static final int SHUTDOWN_PHASE = Phase.EXTERNAL_CONNECTIONS - 100;

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        configurer.onShutdown(SHUTDOWN_PHASE, this::shutdownJanitor);
    }

    private void shutdownJanitor() {
        try {
            logger.debug("Shutting down Axon task janitor executor service");
            AxonTaskJanitor.INSTANCE.shutdown();

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
        }
    }
}
