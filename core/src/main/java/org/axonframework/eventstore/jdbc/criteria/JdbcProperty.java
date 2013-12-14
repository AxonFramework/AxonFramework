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
import org.axonframework.eventstore.management.Property;

/**
 * Property implementation for JPA Event Store.
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 *
 * @since 2.1
 */
public class JdbcProperty implements Property {

    private final String propertyName;

    /**
     * Initializes a property for the given <code>propertyName</code>.
     *
     * @param propertyName The name of the property
     */
    public JdbcProperty(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public JdbcCriteria lessThan(Object expression) {
        return new SimpleOperator(this, "<", expression);
    }

    @Override
    public JdbcCriteria lessThanEquals(Object expression) {
        return new SimpleOperator(this, "<=", expression);
    }

    @Override
    public JdbcCriteria greaterThan(Object expression) {
        return new SimpleOperator(this, ">", expression);
    }

    @Override
    public JdbcCriteria greaterThanEquals(Object expression) {
        return new SimpleOperator(this, ">=", expression);
    }

    @Override
    public JdbcCriteria is(Object expression) {
        return new Equals(this, expression);
    }

    @Override
    public JdbcCriteria isNot(Object expression) {
        return new NotEquals(this, expression);
    }

    @Override
    public Criteria in(Object expression) {
        return new CollectionOperator(this, "IN", expression);
    }

    @Override
    public Criteria notIn(Object expression) {
        return new CollectionOperator(this, "NOT IN", expression);
    }

    /**
     * Parse the property value to a valid EJQL expression.
     *
     * @param entryKey      The variable assigned to the entry holding the property
     * @param stringBuilder The builder to append the expression to
     */
    public void parse(String entryKey, StringBuilder stringBuilder) {
        stringBuilder.append(entryKey)
                     .append(".")
                     .append(propertyName);
    }
}
