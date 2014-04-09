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

import com.mongodb.DBObject;
import org.axonframework.eventstore.management.Criteria;

/**
 * Abstract class for Mongo-based criteria.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class MongoCriteria implements Criteria {

    @Override
    public Criteria and(Criteria criteria) {
        return new And(this, (MongoCriteria) criteria);
    }

    @Override
    public Criteria or(Criteria criteria) {
        return new Or(this, (MongoCriteria) criteria);
    }

    /**
     * Returns the DBObject representing the criterium. This DBObject can be used to select documents in a Mongo Query.
     *
     * @return the DBObject representing the criterium
     */
    public abstract DBObject asMongoObject();

    @Override
    public String toString() {
        return asMongoObject().toString();
    }
}
