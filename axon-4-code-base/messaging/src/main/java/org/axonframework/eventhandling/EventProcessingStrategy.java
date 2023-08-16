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

package org.axonframework.eventhandling;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Interface describing a strategy for the processing of a batch of events. This is used by the {@link
 * SubscribingEventProcessor} to handle events directly (in the same thread) or asynchronously.
 *
 * @author Rene de Waele
 */
public interface EventProcessingStrategy {

    /**
     * Handle the given batch of {@code events}. Once the strategy decides it is opportune to process the events it
     * should pass them back to the given {@code processor}.
     * <p>
     * Note that the strategy may call back to the processor more than once for a single invocation of this method. Also
     * note that a batch of events passed back to the processor may be made up of events from different batches.
     *
     * @param events    Events to be processed
     * @param processor Callback method on the processor that carries out the actual processing of events
     */
    void handle(@Nonnull List<? extends EventMessage<?>> events,
                @Nonnull Consumer<List<? extends EventMessage<?>>> processor);

}
