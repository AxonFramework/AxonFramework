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

package org.axonframework.extension.spring.config;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.LifecycleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A {@link SmartLifecycle} implementation wrapping a
 * {@link LifecycleHandler shutdown-specific lifecycle handler} to allow it to be
 * managed by Spring.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
@Internal
public class SpringLifecycleShutdownHandler implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final int phase;
    private final Supplier<CompletableFuture<?>> task;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Initialize the bean to have the given {@code task} executed on shutdown in the given {@code phase}.
     *
     * @param phase The shutdown phase to invoke the task in.
     * @param task  The task to execute on shutdown.
     */
    SpringLifecycleShutdownHandler(int phase,
                                   @Nonnull Supplier<CompletableFuture<?>> task) {
        this.phase = phase;
        this.task = task;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        try {
            task.get()
                .whenComplete((result, throwable) -> running.set(false))
                // This API forces us to block
                .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            // This is what the join() would throw
            throw new CompletionException(e);
        }
    }

    @Override
    public void stop(@Nonnull Runnable callback) {
        task.get()
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.warn("Failed running shutdown task [{}] in phase [{}].", task, phase, throwable);
                }
                running.set(false);
                callback.run();
            });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return phase;
    }
}
