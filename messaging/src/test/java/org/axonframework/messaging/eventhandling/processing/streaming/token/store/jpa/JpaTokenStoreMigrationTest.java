/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.conversion.TestConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.ConfigToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.UnableToRetrieveIdentifierException;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating migrations of the {@link JpaTokenStore}.
 *
 * @author John Hendrikx
 */
class JpaTokenStoreMigrationTest {

    private final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("tokenstore_migration");
    private final EntityManager entityManager = entityManagerFactory.createEntityManager();
    private final EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);

    private final JpaTokenStore jpaTokenStore = getTokenStore("local");

    @Nested
    class WhenMaskColumnIsMissing {

        {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            entityManager.createNativeQuery("DROP TABLE IF EXISTS TOKENENTRY").executeUpdate();

            // Create a table with a missing mask column
            entityManager.createNativeQuery(
                """
                CREATE TABLE TOKENENTRY (
                    token LONGVARBINARY(10000),
                    tokenType VARCHAR(255),
                    timestamp VARCHAR(255) NOT NULL,
                    owner VARCHAR(255),
                    processorName VARCHAR(255) NOT NULL,
                    segment INT NOT NULL,
                    PRIMARY KEY (processorName, segment)
                )
                """
            ).executeUpdate();

            tx.commit();
        }

        @Test
        void storeShouldNotRetrieveIdentifier() {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            try {
                assertThatThrownBy(() -> jpaTokenStore.retrieveStorageIdentifier(null).join())
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(UnableToRetrieveIdentifierException.class)
                    .cause()
                    .isInstanceOf(SQLGrammarException.class)
                    .hasMessageContaining("object not found: TE1_0.MASK in statement");
            }
            finally {
                tx.commit();
            }
        }

        @Test
        void storeShouldNotInitializeSegments() {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            try {
                assertThatThrownBy(() -> jpaTokenStore.initializeTokenSegments("processor", 5, null, null).join())
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(SQLGrammarException.class)
                    .hasMessageContaining("object not found: TE1_0.MASK in statement");
            }
            finally {
                tx.commit();
            }
        }

        @Test
        void storeShouldNotFetchToken() {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            try {
                assertThatThrownBy(() -> jpaTokenStore.fetchToken("processor", 2, null).join())
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(SQLGrammarException.class)
                    .hasMessageContaining("object not found: TE1_0.MASK in statement");
            }
            finally {
                tx.commit();
            }
        }
    }

    @Nested
    class WhenMaskColumnIsPresentAndStoreIsEmpty {

        {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            entityManager.createNativeQuery("DROP TABLE IF EXISTS TOKENENTRY").executeUpdate();

            entityManager.createNativeQuery(
                """
                CREATE TABLE TOKENENTRY (
                    token LONGVARBINARY(10000),
                    tokenType VARCHAR(255),
                    timestamp VARCHAR(255) NOT NULL,
                    owner VARCHAR(255),
                    processorName VARCHAR(255) NOT NULL,
                    segment INT NOT NULL,
                    mask INT NOT NULL,
                    PRIMARY KEY (processorName, segment)
                )
                """
            ).executeUpdate();

            tx.commit();
        }

        @Test
        void anyCallShouldInitializeAndMigrateStore() {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            try {
                String identifier = jpaTokenStore.retrieveStorageIdentifier(null).join();

                assertThat(identifier).isNotEmpty();

                @SuppressWarnings("unchecked")
                List<TokenEntry> resultList = entityManager.createNativeQuery(
                    """
                    SELECT * FROM TOKENENTRY
                    """,
                    TokenEntry.class
                ).getResultList();

                assertThat(resultList).hasSize(1);

                // assert config token:
                assertThat(resultList.getFirst().getProcessorName()).isEqualTo("__config");
                assertThat(resultList.getFirst().getSegment()).isEqualTo(Segment.ROOT_SEGMENT);
                assertThat(resultList.getFirst().getToken(jpaTokenStore.converter()))
                    .isInstanceOfSatisfying(ConfigToken.class, configToken -> {
                        assertThat(configToken.getConfig().get("id")).isEqualTo(identifier);
                        assertThat(configToken.getConfig().get("version")).isEqualTo("2");
                    });
            }
            finally {
                tx.commit();
            }
        }
    }

    @Nested
    class WhenMaskColumnIsPresentAndThereWerePreMaskTokens {

        {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            entityManager.createNativeQuery("DROP TABLE IF EXISTS TOKENENTRY").executeUpdate();

            entityManager.createNativeQuery(
                """
                CREATE TABLE TOKENENTRY (
                    token LONGVARBINARY(10000),
                    tokenType VARCHAR(255),
                    timestamp VARCHAR(255) NOT NULL,
                    owner VARCHAR(255),
                    processorName VARCHAR(255) NOT NULL,
                    segment INT NOT NULL,
                    mask INT NOT NULL,
                    PRIMARY KEY (processorName, segment)
                )
                """
            ).executeUpdate();

            entityManager.createNativeQuery(
                """
                INSERT INTO TOKENENTRY (processorName, segment, mask, timestamp) VALUES
                    ('my-processor', 1, 0, '2011-11-11T11:11:11Z'),
                    ('my-processor', 2, 0, '2011-11-11T11:11:11Z'),
                    ('my-processor', 3, 0, '2011-11-11T11:11:11Z');
                """
            ).executeUpdate();

            tx.commit();
        }

        @Test
        void anyCallShouldInitializeAndMigrateStore() {
            EntityTransaction tx = entityManager.getTransaction();

            tx.begin();

            try {
                String identifier = jpaTokenStore.retrieveStorageIdentifier(null).join();

                assertThat(identifier).isNotEmpty();

                @SuppressWarnings("unchecked")
                List<TokenEntry> resultList = entityManager.createNativeQuery(
                    """
                    SELECT * FROM TOKENENTRY ORDER BY processorName, segment
                    """,
                    TokenEntry.class
                ).getResultList();

                assertThat(resultList).hasSize(4);

                // assert config token:
                assertThat(resultList.getFirst().getProcessorName()).isEqualTo("__config");
                assertThat(resultList.getFirst().getSegment()).isEqualTo(Segment.ROOT_SEGMENT);
                assertThat(resultList.getFirst().getToken(jpaTokenStore.converter()))
                    .isInstanceOfSatisfying(ConfigToken.class, configToken -> {
                        assertThat(configToken.getConfig().get("id")).isEqualTo(identifier);
                        assertThat(configToken.getConfig().get("version")).isEqualTo("2");
                    });

                // check if masks were migrated:
                for (int i = 1; i <= 3; i++) {
                    TokenEntry entry = resultList.get(i);

                    assertThat(entry.getProcessorName()).isEqualTo("my-processor");
                    assertThat(entry.getSegment()).isEqualTo(new Segment(i, 3));
                }
            }
            finally {
                tx.commit();
            }
        }
    }

    private JpaTokenStore getTokenStore(String nodeId) {
        return new JpaTokenStore(entityManagerProvider, TestConverter.JACKSON.getConverter(), JpaTokenStoreConfiguration.DEFAULT.nodeId(nodeId));
    }
}