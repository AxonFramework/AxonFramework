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
 * Implementation of the EQUALS operator for a JPA Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
class Equals extends JpaCriteria {

    private final JpaProperty propertyName;
    private final Object expression;

    /**
     * Initializes an Equals operator matching the given <code>property</code> against the given
     * <code>expression</code>.
     *
     * @param property   The property to match
     * @param expression The expression to match against. May be <code>null</code>.
     */
    public Equals(JpaProperty property, Object expression) {
        this.propertyName = property;
        this.expression = expression;
    }

    @Override
    public void parse(String entryKey, StringBuilder whereClause, ParameterRegistry parameters) {
        propertyName.parse(entryKey, whereClause);
        if (expression == null) {
            whereClause.append(" IS NULL");
        } else if (expression instanceof JpaProperty) {
            whereClause.append(" = ");
            ((JpaProperty) expression).parse(entryKey, whereClause);
        } else {
            whereClause.append(" = ")
                       .append(parameters.register(expression.toString()));
        }
    }
}
