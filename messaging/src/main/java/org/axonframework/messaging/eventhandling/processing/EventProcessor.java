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

package org.axonframework.messaging.eventhandling.processing;

import org.axonframework.common.infra.DescribableComponent;

import java.util.concurrent.CompletableFuture;

/**
 * An Event Processor processes event messages from an event queue or event bus.
 * <p/>
 * Typically, an Event Processor is in charge of publishing the events to a group of registered handlers. This allows
 * attributes and behavior (e.g. transaction management, asynchronous processing, distribution) to be applied over
 * a whole group at once.
 *
 * @author Allard Buijze
 * @since 1.2.0
 */
public interface EventProcessor extends DescribableComponent {

    /**
     * Returns the name of this event processor. This name is used to detect distributed instances of the
     * same event processor. Multiple instances referring to the same logical event processor (on different JVM's)
     * must have the same name.
     *
     * @return the name of this event processor
     */
    String name();

    /**
     * Initiates a start, providing a {@link CompletableFuture} that completes when the start process is
     * finished.
     *
     * @return a CompletableFuture that completes when the start process is finished.
     */
    CompletableFuture<Void> start();

    /**
     * Indicates whether this processor is currently running (i.e. consuming events from its message source).
     *
     * @return {@code true} when running, otherwise {@code false}
     */
    boolean isRunning();

    /**
     * Indicates whether the processor has been shut down due to an error. In such case, the processor has forcefully
     * shut down, as it wasn't able to automatically recover.
     * <p>
     * Note that this method returns {@code false} when the processor was stopped using {@link #shutdown()}.
     *
     * @return {@code true} when paused due to an error, otherwise {@code false}
     */
    boolean isError();

    /**
     * Initiates a shutdown, providing a {@link CompletableFuture} that completes when the shutdown process is
     * finished.
     *
     * @return a CompletableFuture that completes when the shutdown process is finished.
     */
    CompletableFuture<Void> shutdown();
}
