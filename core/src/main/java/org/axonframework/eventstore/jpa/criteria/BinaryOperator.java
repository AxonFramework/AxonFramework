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
 * Implementation of a binary operator for the Jpa Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class BinaryOperator extends JpaCriteria {

    private final JpaCriteria criteria1;
    private final JpaCriteria criteria2;
    private final String operator;

    /**
     * Initializes a binary operator that matches against two criteria
     *
     * @param criteria1 One of the criteria to match
     * @param operator  The binary operator to check the criteria with
     * @param criteria2 One of the criteria to match
     */
    public BinaryOperator(JpaCriteria criteria1, String operator, JpaCriteria criteria2) {
        this.criteria1 = criteria1;
        this.operator = operator;
        this.criteria2 = criteria2;
    }

    @Override
    public void parse(String entryKey, StringBuilder whereClause, ParameterRegistry parameters) {
        whereClause.append("(");
        criteria1.parse(entryKey, whereClause, parameters);
        whereClause.append(") ")
                   .append(operator)
                   .append(" (");
        criteria2.parse(entryKey, whereClause, parameters);
        whereClause.append(")");
    }
}
