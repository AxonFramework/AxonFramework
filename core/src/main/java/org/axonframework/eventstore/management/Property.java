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

package org.axonframework.eventstore.management;

/**
 * Represents a property of the Domain Event entry stored by an Event Store. Typically, these properties must be the
 * "indexed" values, such as timeStamp, aggregate identifier, etc.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface Property {

    /**
     * Returns a criteria instance where the property must be "less than" the given <code>expression</code>. The given
     * expression may also be a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "less than" requirement.
     */
    Criteria lessThan(Object expression);

    /**
     * Returns a criteria instance where the property must be "less than" or "equal to" the given
     * <code>expression</code>. The given expression may also be a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "less than or equals" requirement.
     */
    Criteria lessThanEquals(Object expression);

    /**
     * Returns a criteria instance where the property must be "greater than" the given <code>expression</code>. The
     * given expression may also be a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "greater than" requirement.
     */
    Criteria greaterThan(Object expression);

    /**
     * Returns a criteria instance where the property must be "greater than" or "equal to" the given
     * <code>expression</code>. The given expression may also be a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "greater than or equals" requirement.
     */
    Criteria greaterThanEquals(Object expression);

    /**
     * Returns a criteria instance where the property must "equal" the given <code>expression</code>. The given
     * expression may also be a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing an "equals" requirement.
     */
    Criteria is(Object expression);

    /**
     * Returns a criteria instance where the property must be "not equal to" the given <code>expression</code>. The
     * given expression may also be a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "not equals" requirement.
     */
    Criteria isNot(Object expression);

    /**
     * Returns a criteria instance where the property must be "in" the given <code>expression</code>. The
     * given expression must be a collection type or a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "is in" requirement.
     */
    Criteria in(Object expression);

    /**
     * Returns a criteria instance where the property must be "not in" the given <code>expression</code>. The
     * given expression must be a collection type or a property.
     *
     * @param expression The expression to match against the property
     * @return a criteria instance describing a "is not in" requirement.
     */
    Criteria notIn(Object expression);
}
