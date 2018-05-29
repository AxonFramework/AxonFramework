/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.commandhandling.model.inspection.AggregateModel;

/**
 * Interface describing objects capable of creating instances of EventStreamLoadingStrategy.
 * 
 * @author André Bierwolf
 * @since 3.3
 */
@FunctionalInterface
public interface EventStreamLoadingStrategyFactory  {
    
    /**
     * Instantiates an EventStreamLoadingStrategy based on the given aggregateModel. 
     *
     * @param aggregateModel the aggregateModel to create the strategy for
     * @return the strategy to use for the given aggregateModel 
     */
    public EventStreamLoadingStrategy create(AggregateModel<?> aggregateModel);
}
