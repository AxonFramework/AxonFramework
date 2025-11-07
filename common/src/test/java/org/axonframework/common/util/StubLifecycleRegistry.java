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

package org.axonframework.common.util;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LifecycleHandler;
import org.axonframework.common.configuration.LifecycleRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Stub implementation of the {@link LifecycleRegistry} interface.
 * <p>
 * This class is used in tests to avoid the need for a full implementation of the {@link LifecycleRegistry} interface.
 * It saves the lifecycle phase timeout and handlers, but does not perform any actual lifecycle management.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class StubLifecycleRegistry  implements LifecycleRegistry {
    private long phaseTimeout = -1;
    private Map<Integer, List<LifecycleHandler>> startHandlers = new ConcurrentHashMap<>();
    private Map<Integer, List<LifecycleHandler>> shutdownHandlers = new ConcurrentHashMap<>();

    @Override
    public LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
        this.phaseTimeout = timeUnit.toMillis(timeout);
        return this;
    }

    @Override
    public LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        startHandlers.computeIfAbsent(phase, k -> new java.util.ArrayList<>()).add(startHandler);
        return this;
    }

    @Override
    public LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        shutdownHandlers.computeIfAbsent(phase, k -> new java.util.ArrayList<>()).add(shutdownHandler);
        return this;
    }

    public long getPhaseTimeout() {
        return phaseTimeout;
    }

    public Map<Integer, List<LifecycleHandler>> getStartHandlers() {
        return new HashMap<>(startHandlers);
    }

    public Map<Integer, List<LifecycleHandler>> getShutdownHandlers() {
        return new HashMap<>(shutdownHandlers);
    }

    /**
     * Executes all start handlers in the order of their phase.
     * @param configuration the configuration to pass to the lifecycle handlers.
     */
    public void start(Configuration configuration) {
        getStartHandlers()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach((entry) -> {
                    for (LifecycleHandler handler : entry.getValue()) {
                        handler.run(configuration);
                    }
                });
    }


    /**
     * Executes all stop handlers in the order of their phase.
     * @param configuration the configuration to pass to the lifecycle handlers.
     */
    public void shutdown(Configuration configuration) {
        getShutdownHandlers()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach((entry) -> {
                    for (LifecycleHandler handler : entry.getValue()) {
                        handler.run(configuration);
                    }
                });
    }
}
