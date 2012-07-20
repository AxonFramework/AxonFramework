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
 * Implementation of all simple operators (i.e. those with a structor of <code>value operator value</code>.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleOperator extends JpaCriteria {

    private final JpaProperty propertyName;
    private final String operator;
    private final Object expression;

    /**
     * Initializes a simple operator where the given <code>property</code>, <code>operator</code> and
     * <code>expression</code> match.
     *
     * @param property   The property to match
     * @param operator   The operator to match with
     * @param expression The expression to match the property against
     */
    public SimpleOperator(JpaProperty property, String operator, Object expression) {
        this.propertyName = property;
        this.operator = operator;
        this.expression = expression;
    }

    @Override
    public void parse(String entryKey, StringBuilder whereClause, ParameterRegistry parameters) {
        propertyName.parse(entryKey, whereClause);
        whereClause.append(" ")
                   .append(operator)
                   .append(" ");
        if (expression instanceof JpaProperty) {
            ((JpaProperty) expression).parse(entryKey, whereClause);
        } else {
            whereClause.append(parameters.register(expression));
        }
    }
}
