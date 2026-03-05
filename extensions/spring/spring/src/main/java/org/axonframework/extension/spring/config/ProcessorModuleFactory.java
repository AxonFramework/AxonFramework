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

import org.jspecify.annotations.NonNull;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;

import java.util.SequencedCollection;
import java.util.Set;

/**
 * Factory for building {@link EventProcessorModule EventProcessorModules} from a set of discovered event handler components.
 * <p>
 * This interface should only be implemented if the default assignment rules for event processors need to be
 * customized beyond what {@link ProcessorDefinition} provides. The factory is responsible for determining which event
 * handlers should be assigned to which processors and creating the corresponding modules.
 * <p>
 * The default implementation ({@link DefaultProcessorModuleFactory}) uses {@link ProcessorDefinition ProcessorDefinitions}
 * to assign handlers based on their selectors. If no matching processor definition is found for a handler, it defaults to
 * assigning the handler to a processor named after the handler's package.
 * <p>
 * Custom implementations can provide completely different assignment logic, such as:
 * <ul>
 *     <li>Assigning handlers based on annotations</li>
 *     <li>Using naming conventions</li>
 *     <li>Grouping by domain boundaries</li>
 *     <li>Implementing custom load balancing strategies</li>
 * </ul>
 *
 * @author Allard Buijze
 * @since 5.0.2
 * @see DefaultProcessorModuleFactory
 * @see ProcessorDefinition
 */
public interface ProcessorModuleFactory {

    /**
     * Builds a set of {@link EventProcessorModule EventProcessorModules} from the given event handler descriptors.
     * <p>
     * Each module represents an event processor with its assigned event handlers. The factory determines the
     * assignment logic and creates appropriately configured modules.
     * <p>
     * <strong>Ordering contract</strong>: {@code handlers} must be a stable, ordered collection (e.g.
     * {@link java.util.LinkedHashSet}). The position of each handler in the collection directly determines the
     * component index used to name its {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue}
     * (e.g. {@code "DeadLetterQueue[processorName][0]"}). When a persistent DLQ backend (JPA, JDBC) is in use,
     * this name is stored in the database as the processing-group identifier. <strong>Changing the registration
     * order of handlers — by adding, removing, or reordering them, or by modifying {@code @Order} values —
     * will shift component indices and cause each handler to be associated with the wrong DLQ data.</strong>
     *
     * @param handlers The ordered collection of discovered event handler components to be assigned to processors.
     *                 Must preserve insertion order (e.g. {@link java.util.LinkedHashSet}).
     * @return A set of event processor modules, each containing its assigned event handlers.
     */
    @NonNull
    Set<EventProcessorModule> buildProcessorModules(@NonNull SequencedCollection<ProcessorDefinition.EventHandlerDescriptor> handlers);
}
