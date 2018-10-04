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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.axonframework.common.AxonConfigurationException;
import org.bson.Document;

import java.util.Objects;

import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * MongoTemplate instance giving direct access to the TrackingToken collection via a given MongoClient instance.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class DefaultMongoTemplate extends AbstractMongoTemplate implements MongoTemplate {

    private final String domainEventsCollectionName;
    private final String snapshotEventsCollectionName;
    private final String trackingTokensCollectionName;
    private final String sagasCollectionName;

    /**
     * Instantiate a {@link DefaultMongoTemplate} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link MongoDatabase} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultMongoTemplate} instance
     */
    protected DefaultMongoTemplate(Builder builder) {
        super(builder);
        this.domainEventsCollectionName = builder.domainEventsCollectionName;
        this.snapshotEventsCollectionName = builder.snapshotEventsCollectionName;
        this.sagasCollectionName = builder.sagasCollectionName;
        this.trackingTokensCollectionName = builder.trackingTokensCollectionName;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultMongoTemplate}.
     * <p>
     * The {@code domainEventsCollectionName}, {@code snapshotEventsCollectionName},
     * {@code trackingTokensCollectionName} and (@code sagasCollectionName} are respectively defaulted to
     * {@code trackingtokens}, {@code domainevents}, {@code snapshotevents} and {@code sagas}.
     * <p>
     * The {@link MongoDatabase} is a <b>hard requirement</b> and as such should be provided. Can either be provided
     * directly, or by setting a {@link MongoClient}. When choosing the latter approach, the MongoDatabase name can be
     * specified by using the {@link Builder#mongoDatabase(MongoClient, String)} function. Otherwise, the database name
     * defaults to {@link AbstractMongoTemplate.Builder#DEFAULT_AXONFRAMEWORK_DATABASE}
     *
     * @return a Builder to be able to create a {@link DefaultMongoTemplate}
     */
    public static Builder builder() {
        return new Builder();
    }

    public DefaultMongoTemplate withSnapshotCollection(String snapshotEventsCollectionName) {
        return DefaultMongoTemplate.builder()
                                   .mongoDatabase(database())
                                   .domainEventsCollectionName(domainEventsCollectionName)
                                   .snapshotEventsCollectionName(snapshotEventsCollectionName)
                                   .sagasCollectionName(sagasCollectionName)
                                   .trackingTokensCollectionName(trackingTokensCollectionName)
                                   .build();
    }

    public DefaultMongoTemplate withDomainEventsCollection(String domainEventsCollectionName) {
        return DefaultMongoTemplate.builder()
                                   .mongoDatabase(database())
                                   .domainEventsCollectionName(domainEventsCollectionName)
                                   .snapshotEventsCollectionName(snapshotEventsCollectionName)
                                   .sagasCollectionName(sagasCollectionName)
                                   .trackingTokensCollectionName(trackingTokensCollectionName)
                                   .build();
    }

    public DefaultMongoTemplate withSagasCollection(String sagasCollectionName) {
        return DefaultMongoTemplate.builder()
                                   .mongoDatabase(database())
                                   .domainEventsCollectionName(domainEventsCollectionName)
                                   .snapshotEventsCollectionName(snapshotEventsCollectionName)
                                   .sagasCollectionName(sagasCollectionName)
                                   .trackingTokensCollectionName(trackingTokensCollectionName)
                                   .build();
    }

    public DefaultMongoTemplate withTrackingTokenCollection(String trackingTokensCollectionName) {
        return DefaultMongoTemplate.builder()
                                   .mongoDatabase(database())
                                   .domainEventsCollectionName(domainEventsCollectionName)
                                   .snapshotEventsCollectionName(snapshotEventsCollectionName)
                                   .sagasCollectionName(sagasCollectionName)
                                   .trackingTokensCollectionName(trackingTokensCollectionName)
                                   .build();
    }

    @Override
    public MongoCollection<Document> trackingTokensCollection() {
        return database().getCollection(trackingTokensCollectionName);
    }

    @Override
    public MongoCollection<Document> eventCollection() {
        return database().getCollection(domainEventsCollectionName);
    }

    @Override
    public MongoCollection<Document> snapshotCollection() {
        return database().getCollection(snapshotEventsCollectionName);
    }

    @Override
    public MongoCollection<Document> sagaCollection() {
        return database().getCollection(sagasCollectionName);
    }

    /**
     * Builder class to instantiate a {@link DefaultMongoTemplate}.
     * <p>
     * The {@code domainEventsCollectionName}, {@code snapshotEventsCollectionName},
     * {@code trackingTokensCollectionName} and (@code sagasCollectionName} are respectively defaulted to
     * {@code trackingtokens}, {@code domainevents}, {@code snapshotevents} and {@code sagas}.
     * <p>
     * The {@link MongoDatabase} is a <b>hard requirement</b> and as such should be provided. Can either be provided
     * directly, or by setting a {@link MongoClient}. When choosing the latter approach, the MongoDatabase name can be
     * specified by using the {@link Builder#mongoDatabase(MongoClient, String)} function. Otherwise, the database name
     * defaults to {@link AbstractMongoTemplate.Builder#DEFAULT_AXONFRAMEWORK_DATABASE}
     */
    public static class Builder extends AbstractMongoTemplate.Builder {

        private String domainEventsCollectionName = "domainevents";
        private String snapshotEventsCollectionName = "snapshotevents";
        private String trackingTokensCollectionName = "trackingtokens";
        private String sagasCollectionName = "sagas";

        @Override
        public Builder mongoDatabase(MongoClient mongoClient) {
            super.mongoDatabase(mongoClient);
            return this;
        }

        @Override
        public Builder mongoDatabase(MongoClient mongoClient, String databaseName) {
            super.mongoDatabase(mongoClient, databaseName);
            return this;
        }

        @Override
        public Builder mongoDatabase(MongoDatabase mongoDatabase) {
            super.mongoDatabase(mongoDatabase);
            return this;
        }

        /**
         * Sets the {@code domainEventsCollectionName} to use as the collection name for Domain Events.
         *
         * @param domainEventsCollectionName a {@link String} specifying the collection name for Domain Events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder domainEventsCollectionName(String domainEventsCollectionName) {
            assertName(domainEventsCollectionName, "domainEventsCollectionName");
            this.domainEventsCollectionName = domainEventsCollectionName;
            return this;
        }

        /**
         * Sets the {@code snapshotEventsCollectionName} to use as the collection name for Snapshot Events.
         *
         * @param snapshotEventsCollectionName a {@link String} specifying the collection name for Snapshot Events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder snapshotEventsCollectionName(String snapshotEventsCollectionName) {
            assertName(snapshotEventsCollectionName, "snapshotEventsCollectionName");
            this.snapshotEventsCollectionName = snapshotEventsCollectionName;
            return this;
        }

        /**
         * Sets the {@code trackingTokensCollectionName} to use as the collection name for Tracking Tokens.
         *
         * @param trackingTokensCollectionName a {@link String} specifying the collection name for Tracking Tokens
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder trackingTokensCollectionName(String trackingTokensCollectionName) {
            assertName(trackingTokensCollectionName, "trackingTokensCollectionName");
            this.trackingTokensCollectionName = trackingTokensCollectionName;
            return this;
        }

        /**
         * Sets the {@code sagasCollectionName} to use as the collection name for Saga instances.
         *
         * @param sagasCollectionName a {@link String} specifying the collection name for Sagas
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder sagasCollectionName(String sagasCollectionName) {
            assertName(sagasCollectionName, "sagasCollectionName");
            this.sagasCollectionName = sagasCollectionName;
            return this;
        }

        private void assertName(String collectionName, String collectionDescription) {
            assertThat(collectionName,
                       name -> Objects.nonNull(name) && !"".equals(name),
                       String.format("The %s may not be null", collectionDescription));
        }

        /**
         * Initializes a {@link DefaultMongoTemplate} as specified through this Builder.
         *
         * @return a {@link DefaultMongoTemplate} as specified through this Builder
         */
        public DefaultMongoTemplate build() {
            return new DefaultMongoTemplate(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
        }
    }
}
