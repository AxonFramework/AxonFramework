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

package org.axonframework.eventstore.jdbc.criteria;

import org.axonframework.eventstore.management.Criteria;

/**
 * Abstract implementation of the Criteria interface for a JPA Event Store.
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 *
 * @since 2.1
 */
public abstract class JdbcCriteria implements Criteria {

    @Override
    public JdbcCriteria and(Criteria criteria) {
        return new BinaryOperator(this, "AND", (JdbcCriteria) criteria);
    }

    @Override
    public JdbcCriteria or(Criteria criteria) {
        return new BinaryOperator(this, "OR", (JdbcCriteria) criteria);
    }

    /**
     * Parses the criteria to a JPA compatible where clause and parameter values.
     *
     * @param entryKey    The variable assigned to the entry in the whereClause
     * @param whereClause The buffer to write the where clause to.
     * @param parameters  The registry where parameters and assigned values can be registered.
     */
    public abstract void parse(String entryKey, StringBuilder whereClause, ParameterRegistry parameters);
}
