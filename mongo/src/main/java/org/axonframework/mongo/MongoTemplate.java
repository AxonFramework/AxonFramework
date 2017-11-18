/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.mongo;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

/**
 * Template object providing access to the collections necessary for the Mongo based components.
 */
public interface MongoTemplate {

    /**
     * Returns a reference to the collection containing the Tracking Tokens.
     *
     * @return MongoCollection containing the Tracking Tokens
     */
    MongoCollection<Document> trackingTokensCollection();

    /**
     * Returns a reference to the collection containing the events.
     *
     * @return MongoCollection containing the domain events
     */
    MongoCollection<Document> eventCollection();

    /**
     * Returns a reference to the collection containing the snapshot events.
     *
     * @return MongoCollection containing the snapshot events
     */
    MongoCollection<Document> snapshotCollection();

    /**
     * Returns a reference to the collection containing the saga instances.
     *
     * @return MongoCollection containing the sagas
     */
    MongoCollection<Document> sagaCollection();

}
