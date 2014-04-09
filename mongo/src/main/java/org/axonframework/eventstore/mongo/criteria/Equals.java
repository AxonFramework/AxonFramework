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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.axonframework.common.Assert;

/**
 * Representation of an Equals operator for Mongo selection criteria.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class Equals extends MongoCriteria {

    private final MongoProperty property;
    private final Object expression;

    /**
     * Creates an equal instance that requires the given property to equal the given <code>expression</code>. The
     * expression may be either a fixed value, or another MongoProperty.
     *
     * @param property   The property to evaluate
     * @param expression The expression to compare the property with
     */
    public Equals(MongoProperty property, Object expression) {
        Assert.isFalse(expression instanceof MongoProperty,
                       "The MongoEventStore does not support comparison between two properties");

        this.property = property;
        this.expression = expression;
    }

    @Override
    public DBObject asMongoObject() {
        return new BasicDBObject(property.getName(), expression.toString());
    }
}
