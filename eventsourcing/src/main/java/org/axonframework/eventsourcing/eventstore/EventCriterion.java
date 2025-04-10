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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.messaging.QualifiedName;

import java.util.Set;

/**
 * Describes a singular, non-nested criteria for filtering events. Can be acquired by using the
 * {@link EventCriteria#flatten()} method.
 *
 * @author Mitchell Herrijgers
 * @see EventCriteria
 * @since 5.0.0
 */
public sealed interface EventCriterion extends EventCriteria permits TagAndTypeFilteredEventCriteria, TagFilteredEventCriteria {

    /**
     * A {@link Set} of {@link String Strings} containing all the types of events applicable for sourcing, streaming, or
     * appending events.
     *
     * @return The {@link Set} of {@link String Strings} containing all the types of events applicable for sourcing, streaming,
     * or appending events.
     */
    Set<QualifiedName> types();

    /**
     * A {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events. A {@code Tag} can, for
     * example, refer to an entities' (aggregate) identifier name and value.
     *
     * @return The {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events.
     */
    Set<Tag> tags();
}
