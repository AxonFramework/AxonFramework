/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventstore.management;

/**
 * Interface providing access to the criteria API of an Event Store.
 * <p/>
 * <em>Example:</em><br/>
 * <pre>
 *     CriteriaBuilder entry = eventStore.newCriteriaBuilder();
 *     // Timestamps are stored as ISO 8601 Strings.
 *     Criteria criteria = entry.property("timeStamp").greaterThan("2011-11-12");
 *     eventStore.visitEvents(criteria, visitor);
 * </pre>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CriteriaBuilder {

    /**
     * Returns a property instance that can be used to build criteria. The given <code>propertyName</code> must hold a
     * valid value for the Event Store that returns that value. Typically, it requires the "indexed" values to be used,
     * such as event identifier, aggregate identifier, timestamp, etc.
     *
     * @param propertyName The name of the property to evaluate
     * @return a property instance that can be used to build expressions
     */
    Property property(String propertyName);
}
