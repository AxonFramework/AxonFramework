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

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.axonframework.common.AxonConfigurationException;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation for Mongo templates. Mongo templates give access to the collections in a Mongo Database used
 * by components of the Axon Framework.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractMongoTemplate {

    private final MongoDatabase database;

    /**
     * Instantiate a {@link AbstractMongoTemplate} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link MongoDatabase} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractMongoTemplate} instance
     */
    protected AbstractMongoTemplate(Builder builder) {
        builder.validate();
        this.database = builder.database;
    }

    /**
     * Returns a reference to the Database with the configured database name. If a username and/or password have been
     * provided, these are used to authenticate against the database.
     * <p/>
     * Note that the configured {@code userName} and {@code password} are ignored if the database is already
     * in an authenticated state.
     *
     * @return a DB instance, referring to the database with configured name.
     */
    protected MongoDatabase database() {
        return database;
    }

    /**
     * Builder class to instantiate a {@link AbstractMongoTemplate}.
     * <p>
     * The {@link MongoDatabase} is a <b>hard requirement</b> and as such should be provided. Can either be provided
     * directly, or by setting a {@link MongoClient}. When choosing the latter approach, the MongoDatabase name can be
     * specified by using the {@link Builder#mongoDatabase(MongoClient, String)} function. Otherwise, the database name
     * defaults to {@link AbstractMongoTemplate.Builder#DEFAULT_AXONFRAMEWORK_DATABASE}
     */
    public abstract static class Builder {

        private static final String DEFAULT_AXONFRAMEWORK_DATABASE = "axonframework";

        private MongoDatabase database;

        /**
         * Sets the {@link MongoDatabase} by means of providing a {@link MongoClient}. The
         * {@link MongoClient#getDatabase(String)} function is in turn used to retrieve the MongoDatabase, using
         * {@link AbstractMongoTemplate.Builder#DEFAULT_AXONFRAMEWORK_DATABASE} as the database name.
         *
         * @param mongoClient the {@link MongoClient} used to retrieve a {@link MongoDatabase} from
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder mongoDatabase(MongoClient mongoClient) {
            return mongoDatabase(mongoClient, DEFAULT_AXONFRAMEWORK_DATABASE);
        }

        /**
         * Sets the {@link MongoDatabase} by means of providing a {@link MongoClient}. The
         * {@link MongoClient#getDatabase(String)} function is in turn used to retrieve the MongoDatabase using
         * {@code databaseName} as the database name.
         *
         * @param mongoClient  the {@link MongoClient} used to retrieve a {@link MongoDatabase} from
         * @param databaseName a {@link String} denoting the name of the {@link MongoDatabase}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder mongoDatabase(MongoClient mongoClient, String databaseName) {
            assertNonNull(mongoClient, "MongoClient may not be null");
            this.database = mongoClient.getDatabase(databaseName);
            return this;
        }

        /**
         * Sets the {@link MongoDatabase} used by this template to connect to a Mongo instance.
         *
         * @param mongoDatabase the {@link MongoDatabase} used by this template to connect to a Mongo instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder mongoDatabase(MongoDatabase mongoDatabase) {
            assertNonNull(mongoDatabase, "MongoDatabase may not be null");
            this.database = mongoDatabase;
            return this;
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(database, "The MongoDatabase is a hard requirement and should be provided");
        }
    }
}
