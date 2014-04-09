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
 * Implementation of the simple Mongo Operators (those without special structural requirements), such as Less Than,
 * Less Than Equals, etc.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleMongoOperator extends MongoCriteria {

    private final MongoProperty property;
    private final String operator;
    private final Object expression;

    /**
     * Initializes an criterium where the given <code>property</code>, <code>operator</code> and
     * <code>expression</code>
     * make a match. The expression may be a fixed value, as well as a MongoProperty
     *
     * @param property   The property to match
     * @param operator   The operator to match with
     * @param expression The expression to match against the property
     */
    public SimpleMongoOperator(MongoProperty property, String operator, Object expression) {
        this.property = property;
        this.operator = operator;
        this.expression = expression;
        Assert.isFalse(expression instanceof MongoProperty,
                       "The MongoEventStore does not support comparison between two properties");
    }

    @Override
    public DBObject asMongoObject() {
        return new BasicDBObject(property.getName(), new BasicDBObject(operator, expression.toString()));
    }
}
