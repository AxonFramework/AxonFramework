/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventstore.jpa.criteria;

/**
 * Implementation of the Or operator for the JPA Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
class Or extends JpaCriteria {

    private final JpaCriteria criteria1;
    private final JpaCriteria criteria2;

    /**
     * Initializes an OR operator, where either of the given criteria must be <code>true</code>.
     *
     * @param criteria1 One of the criteria to match
     * @param criteria2 One of the criteria to match
     */
    public Or(JpaCriteria criteria1, JpaCriteria criteria2) {
        this.criteria1 = criteria1;
        this.criteria2 = criteria2;
    }

    @Override
    public void parse(String entryKey, StringBuilder whereClause, ParameterRegistry parameters) {
        whereClause.append("(");
        criteria1.parse(entryKey, whereClause, parameters);
        whereClause.append(") OR (");
        criteria2.parse(entryKey, whereClause, parameters);
        whereClause.append(")");
    }
}
