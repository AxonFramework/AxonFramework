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

import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdate;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

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
    private DeadLetterStatementFactory<EventMessage<?>> statementFactory;
    private JdbcSequencedDeadLetterQueue<EventMessage<?>> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildDeadLetterQueue() {
        dataSource = dataSource();
        transactionManager = transactionManager(dataSource);
        Serializer eventSerializer = TestSerializer.JACKSON.getSerializer();
        Serializer genericSerializer = TestSerializer.JACKSON.getSerializer();
        statementFactory = DefaultDeadLetterStatementFactory.builder()
                                                            .eventSerializer(eventSerializer)
                                                            .genericSerializer(genericSerializer)
                                                            .schema(schema)
                                                            .build();

        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .connectionProvider(dataSource::getConnection)
                                                          .schema(schema)
                                                          .statementFactory(statementFactory)
                                                          .transactionManager(transactionManager)
                                                          .genericSerializer(eventSerializer)
                                                          .eventSerializer(genericSerializer)
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

    @Test
    void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
        String aggregateId = UUID.randomUUID().toString();
        Map<Integer, GenericDeadLetter<EventMessage<?>>> insertedLetters = new HashMap<>();

        Iterator<DeadLetter<? extends EventMessage<?>>> resultIterator =
                jdbcDeadLetterQueue.deadLetterSequence(aggregateId)
                                   .iterator();
        assertFalse(resultIterator.hasNext());

        IntStream.range(0, 64)
                 .boxed()
                 .sorted(Collections.reverseOrder())
                 .forEach(i -> {
                     GenericDeadLetter<EventMessage<?>> letter =
                             new GenericDeadLetter<>(aggregateId, asEventMessage(i));
                     insertLetterAtIndex(aggregateId, letter, i);
                     insertedLetters.put(i, letter);
                 });

        resultIterator = jdbcDeadLetterQueue.deadLetterSequence(aggregateId).iterator();
        for (Map.Entry<Integer, GenericDeadLetter<EventMessage<?>>> entry : insertedLetters.entrySet()) {
            Integer sequenceIndex = entry.getKey();
            Supplier<String> assertMessageSupplier = () -> "Failed asserting event [" + sequenceIndex + "]";
            assertTrue(resultIterator.hasNext(), assertMessageSupplier);

            GenericDeadLetter<EventMessage<?>> expected = entry.getValue();
            DeadLetter<? extends EventMessage<?>> result = resultIterator.next();
            assertTrue(result instanceof JdbcDeadLetter);
            JdbcDeadLetter<? extends EventMessage<?>> actual = ((JdbcDeadLetter<? extends EventMessage<?>>) result);

            assertEquals(expected.getSequenceIdentifier(), actual.getSequenceIdentifier(), assertMessageSupplier);
            assertEquals(expected.message().getPayload(), actual.message().getPayload(), assertMessageSupplier);
            assertFalse(result.cause().isPresent(), assertMessageSupplier);
            assertEquals(expected.diagnostics(), actual.diagnostics(), assertMessageSupplier);
            assertEquals(sequenceIndex.longValue(), actual.getSequenceIndex(), assertMessageSupplier);
        }
    }

    private void insertLetterAtIndex(String aggregateId, DeadLetter<EventMessage<?>> letter, int index) {
        transactionManager.executeInTransaction(() -> {
            try (Connection connection = dataSource.getConnection()) {
                executeUpdate(
                        connection,
                        c -> statementFactory.enqueueStatement(
                                c, TEST_PROCESSING_GROUP, aggregateId, letter, index
                        ),
                        e -> new JdbcException(
                                "Failed to enqueue dead letter with with message id [" +
                                        letter.message().getIdentifier() + "] during testing", e
                        )
                );
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
