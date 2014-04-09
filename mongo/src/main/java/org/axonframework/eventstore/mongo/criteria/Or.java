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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Represents the OR operator for use by the Mongo Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class Or extends MongoCriteria {

    private final MongoCriteria criteria1;
    private final MongoCriteria criteria2;

    /**
     * Initializes an OR operator where one of the given criteria must evaluate to <code>true</code>.
     *
     * @param criteria1 One of the criteria that must evaluate to true
     * @param criteria2 One of the criteria that must evaluate to true
     */
    public Or(MongoCriteria criteria1, MongoCriteria criteria2) {
        this.criteria1 = criteria1;
        this.criteria2 = criteria2;
    }

    @Override
    public DBObject asMongoObject() {
        BasicDBList value = new BasicDBList();
        value.add(criteria1.asMongoObject());
        value.add(criteria2.asMongoObject());
        return new BasicDBObject("$or", value);
    }
}
