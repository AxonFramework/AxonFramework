/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.repository.AggregateNotFoundException;

/**
 * Special case of the {@link org.axonframework.repository.AggregateNotFoundException} that indicates that historic
 * information of an aggregate was found, but the aggregate has been deleted.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public class AggregateDeletedException extends AggregateNotFoundException {

    private static final long serialVersionUID = 6814686444144567614L;

    /**
     * Initialize a AggregateDeletedException for an aggregate identifier given by <code>aggregateType</code> and <code>aggregateIdentifier</code>
     * and given <code>message</code>.
     *
     * @param aggregateType       The type of the aggregate that has been deleted
     * @param aggregateIdentifier The identifier of the aggregate that has been deleted
     * @param message             The message describing the cause of the exception
     */
    public AggregateDeletedException(Class<?> aggregateType, Object aggregateIdentifier, String message) {
        super(aggregateType, aggregateIdentifier, message);
    }

    /**
     * Initialize a AggregateDeletedException for an aggregate identifier by given <code>aggregateIdentifier</code> and
     * given <code>message</code>.
     *
     * @param aggregateIdentifier The identifier of the aggregate that has been deleted
     * @param message             The message describing the cause of the exception
     */
    public AggregateDeletedException(Object aggregateIdentifier, String message) {
        super(aggregateIdentifier, message);
    }

    /**
     * Initialize a AggregateDeletedException for an aggregate identifier by given <code>aggregateIdentifier</code> and
     * a default <code>message</code>.
     *
     * @param aggregateType       The type of the aggregate that has been deleted
     * @param aggregateIdentifier The identifier of the aggregate that has been deleted
     */
    public AggregateDeletedException(Class<?> aggregateType, Object aggregateIdentifier) {
        this(aggregateType, aggregateIdentifier,
             String.format("Aggregate with identifier [%s] not found. It has been deleted.", aggregateIdentifier));
    }

    /**
     * Initialize a AggregateDeletedException for an aggregate identifier by given <code>aggregateIdentifier</code> and
     * a default <code>message</code>.
     *
     * @param aggregateIdentifier The identifier of the aggregate that has been deleted
     */
    public AggregateDeletedException(Object aggregateIdentifier) {
        this((Class<?>) null, aggregateIdentifier);
    }
}
