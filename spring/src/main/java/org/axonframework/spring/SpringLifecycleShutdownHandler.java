/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.spring;

import org.springframework.context.SmartLifecycle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class SpringLifecycleShutdownHandler implements SmartLifecycle {

    private final int phase;
    private final Supplier<CompletableFuture<?>> task;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SpringLifecycleShutdownHandler(int phase, Supplier<CompletableFuture<?>> task) {
        this.phase = phase;
        this.task = task;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        task.get().whenComplete((result, throwable) -> running.set(false));
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
