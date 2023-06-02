/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jdbc;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link JdbcSequencedDeadLetterQueue} with an {@link org.axonframework.eventhandling.EventProcessor} and
 * {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Steven van Beelen
 */
class JdbcDeadLetteringEventIntegrationTest extends DeadLetteringEventIntegrationTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    private DataSource dataSource;
    private TransactionManager transactionManager;
    private JdbcSequencedDeadLetterQueue<EventMessage<?>> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildDeadLetterQueue() {
        dataSource = dataSource();
        transactionManager = transactionManager(dataSource);
        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .connectionProvider(dataSource::getConnection)
                                                          .schema(schema)
                                                          .transactionManager(transactionManager)
                                                          .genericSerializer(TestSerializer.JACKSON.getSerializer())
                                                          .eventSerializer(TestSerializer.JACKSON.getSerializer())
                                                          .build();
        return jdbcDeadLetterQueue;
    }

    private DataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:axontest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private TransactionManager transactionManager(DataSource dataSource) {
        PlatformTransactionManager platformTransactionManager = new DataSourceTransactionManager(dataSource);
        return () -> {
            TransactionStatus transaction =
                    platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
            return new Transaction() {
                @Override
                public void commit() {
                    platformTransactionManager.commit(transaction);
                }

                @Override
                public void rollback() {
                    platformTransactionManager.rollback(transaction);
                }
            };
        };
    }

    @BeforeEach
    void setUpJdbc() {
        transactionManager.executeInTransaction(() -> {
            // Clear current DLQ
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                //noinspection SqlDialectInspection,SqlNoDataSourceInspection
                connection.prepareStatement("DROP TABLE IF EXISTS " + schema.deadLetterTable())
                          .executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Enable to retrieve a Connection to drop the dead-letter queue", e);
            } finally {
                closeQuietly(connection);
            }
            // Construct new DLQ
            jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory());
        });
    }
}
