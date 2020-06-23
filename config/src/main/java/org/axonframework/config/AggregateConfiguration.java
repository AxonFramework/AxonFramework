/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.command.Repository;

/**
 * Specialization of the Module Configuration for modules that define an Aggregate Configuration. This interface allows
 * components to retrieve the Repository used to load Aggregates of the type defined in this Configuration.
 *
 * @param <A> The type of Aggregate defined in this Configuration
 * @author Allard Buijze
 * @since 3.0
 */
public interface AggregateConfiguration<A> extends ModuleConfiguration {

    /**
     * Returns the repository defined to load instances of the Aggregate type defined in this configuration.
     *
     * @return the repository to load aggregates
     */
    Repository<A> repository();

    /**
     * Returns the type of Aggregate defined in this configuration.
     *
     * @return the type of Aggregate defined in this configuration
     */
    Class<A> aggregateType();

    /**
     * Returns the {@link AggregateFactory} defined in this configuration.
     *
     * @return the {@link AggregateFactory} defined in this configuration.
     */
    AggregateFactory<A> aggregateFactory();

    /**
     * Returns the {@link SnapshotFilter} defined in this configuration.
     *
     * @return the {@link SnapshotFilter} defined in this configuration
     */
    SnapshotFilter snapshotFilter();
}
