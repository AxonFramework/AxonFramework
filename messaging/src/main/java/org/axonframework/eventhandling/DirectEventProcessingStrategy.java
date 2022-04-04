/*
 * Copyright (c) 2010-2022. Axon Framework
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
 * Event processing strategy that directly initiates event processing.
 *
 * @author Rene de Waele
 */
public enum DirectEventProcessingStrategy implements EventProcessingStrategy {
    /**
     * Singleton instance of the {@link DirectEventProcessingStrategy}.
     */
    INSTANCE;

    @Override
    public void handle(@Nonnull List<? extends EventMessage<?>> events,
                       @Nonnull Consumer<List<? extends EventMessage<?>>> processor) {
        processor.accept(events);
    }
}
