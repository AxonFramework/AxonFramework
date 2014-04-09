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

package org.axonframework.eventstore.jdbc.criteria;

/**
 * Abstract implementation to use for testing whether an item is present in a collection or not.
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.2
 */
public class CollectionOperator extends JdbcCriteria {

    private final JdbcProperty property;
    private final Object expression;
    private final String operator;

    /**
     * Initializes the operator matching given <code>property</code> against the given <code>expression</code> using
     * the given <code>operator</code>.
     *
     * @param property   The property to match
     * @param operator   The JPA operator to match the property against the expression
     * @param expression The expression to match against
     */
    public CollectionOperator(JdbcProperty property, String operator, Object expression) {
        this.property = property;
        this.expression = expression;
        this.operator = operator;
    }

    @Override
    public void parse(String entryKey, StringBuilder whereClause, ParameterRegistry parameters) {
        property.parse(entryKey, whereClause);
        whereClause.append(" ")
                   .append(operator)
                   .append(" ");
        if (expression instanceof JdbcProperty) {
            ((JdbcProperty) expression).parse(entryKey, whereClause);
        } else {
            whereClause.append("(")
                       .append(parameters.register(expression))
                       .append(")");
        }
    }
}
