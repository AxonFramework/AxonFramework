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

package org.axonframework.eventstore.mongo.criteria;

import org.axonframework.eventstore.management.Property;

/**
 * Property implementation for use by the Mongo Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MongoProperty implements Property {

    private final String propertyName;

    /**
     * Initialize a property for the given <code>propertyName</code>.
     *
     * @param propertyName The name of the property of the Mongo document.
     */
    public MongoProperty(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public MongoCriteria lessThan(Object expression) {
        return new SimpleMongoOperator(this, "$lt", expression);
    }

    @Override
    public MongoCriteria lessThanEquals(Object expression) {
        return new SimpleMongoOperator(this, "$lte", expression);
    }

    @Override
    public MongoCriteria greaterThan(Object expression) {
        return new SimpleMongoOperator(this, "$gt", expression);
    }

    @Override
    public MongoCriteria greaterThanEquals(Object expression) {
        return new SimpleMongoOperator(this, "$gte", expression);
    }

    @Override
    public MongoCriteria is(Object expression) {
        return new Equals(this, expression);
    }

    @Override
    public MongoCriteria isNot(Object expression) {
        return new SimpleMongoOperator(this, "$ne", expression);
    }

    @Override
    public MongoCriteria in(Object expression) {
        return new CollectionCriteria(this, "$in", expression);
    }

    @Override
    public MongoCriteria notIn(Object expression) {
        return new CollectionCriteria(this, "$nin", expression);
    }

    /**
     * Returns the name of the property.
     *
     * @return the name of the property
     */
    public String getName() {
        return propertyName;
    }
}
