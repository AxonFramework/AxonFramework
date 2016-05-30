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

package org.axonframework.eventhandling.saga.repository.mongo;

import com.mongodb.DBCollection;

/**
 * <p>Generic template for accessing Mongo for the axon sagas.</p>
 * <p/>
 * <p>You can ask for the collection of domain events as well as the collection of sagas and association values. We
 * use the mongo client mongo-java-driver. This is a wrapper around the standard mongo methods. For convenience the
 * interface also gives access to the database that contains the axon saga collections.</p>
 * <p/>
 * <p>Implementations of this interface must provide the connection to Mongo.</p>
 *
 * @author Jettro Coenradie
 * @since 2.0
 */
public interface MongoTemplate {
    /**
     * Returns a reference to the collection containing the saga instances.
     *
     * @return DBCollection containing the sagas
     */
    DBCollection sagaCollection();

}