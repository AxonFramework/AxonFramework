/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter.jdbc;

import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;

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

import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdate;
import static org.axonframework.messaging.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link JdbcSequencedDeadLetterQueue} with an {@link EventProcessor} and
 * {@code DeadLetteringEventHandlingComponent}.
 * <p>
 * Note: inherited tests from the base class that compare payloads directly are disabled because serialized DLQs
 * (JDBC, JPA) store payloads as raw bytes. The base test assumes deserialized objects (InMemory DLQ pattern). The JPA
 * module does not have this integration test for the same reason.
 *
 * @author Steven van Beelen
 */
@Disabled("Serialized DLQs store payloads as raw bytes; inherited integration tests expect deserialized objects. "
        + "JDBC-specific tests are in JdbcSequencedDeadLetterQueueTest.")
class JdbcDeadLetteringEventIntegrationTest extends DeadLetteringEventIntegrationTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    private DataSource dataSource;
    // Sentinel connection to keep HSQLDB in-memory database alive across operations
    private Connection sentinelConnection;
    private TransactionalExecutor<Connection> executor;
    private DeadLetterStatementFactory<EventMessage> statementFactory;
    private JdbcSequencedDeadLetterQueue<EventMessage> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
    private final JacksonConverter jacksonConverter = new JacksonConverter();
    private final EventConverter eventConverter = new DelegatingEventConverter(jacksonConverter);

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        dataSource = dataSource();
        try {
            sentinelConnection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open sentinel connection", e);
        }
        Converter genericConverter = jacksonConverter;
        statementFactory = DefaultDeadLetterStatementFactory.<EventMessage>builder()
                                                            .eventConverter(eventConverter)
                                                            .genericConverter(genericConverter)
                                                            .schema(schema)
                                                            .build();

        // Use a provider that ignores ProcessingContext and always creates a new DataSource-based executor.
        // In production, ProcessingContext would carry a ConnectionExecutor resource, but in tests the
        // event processing pipeline doesn't set up JDBC transaction integration.
        JdbcTransactionalExecutorProvider baseProvider = new JdbcTransactionalExecutorProvider(dataSource);
        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.<EventMessage>builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .transactionalExecutorProvider(
                                                                  pc -> baseProvider.getTransactionalExecutor(null)
                                                          )
                                                          .schema(schema)
                                                          .statementFactory(statementFactory)
                                                          .eventConverter(eventConverter)
                                                          .genericConverter(genericConverter)
                                                          .build();
        executor = baseProvider.getTransactionalExecutor(null);
        return jdbcDeadLetterQueue;
    }

    private DataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:dlqintegtest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @AfterEach
    void tearDown() {
        closeQuietly(sentinelConnection);
    }

    @BeforeEach
    void setUpJdbc() {
        // Clear current DLQ
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            //noinspection SqlDialectInspection,SqlNoDataSourceInspection
            connection.prepareStatement("DROP TABLE IF EXISTS " + schema.deadLetterTable())
                      .executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve a Connection to drop the dead-letter queue", e);
        } finally {
            closeQuietly(connection);
        }
        // Construct new DLQ
        jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory());
    }

    @Test
    void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
        String aggregateId = UUID.randomUUID().toString();
        Map<Integer, GenericDeadLetter<EventMessage>> insertedLetters = new HashMap<>();

        Iterator<DeadLetter<? extends EventMessage>> resultIterator =
                joinAndUnwrap(jdbcDeadLetterQueue.deadLetterSequence(aggregateId, null))
                        .iterator();
        assertFalse(resultIterator.hasNext());

        IntStream.range(0, 64)
                 .boxed()
                 .sorted(Collections.reverseOrder())
                 .forEach(i -> {
                     GenericDeadLetter<EventMessage> letter =
                             new GenericDeadLetter<>(aggregateId, asEventMessage(i));
                     insertLetterAtIndex(aggregateId, letter, i);
                     insertedLetters.put(i, letter);
                 });

        resultIterator = joinAndUnwrap(jdbcDeadLetterQueue.deadLetterSequence(aggregateId, null)).iterator();
        for (Map.Entry<Integer, GenericDeadLetter<EventMessage>> entry : insertedLetters.entrySet()) {
            Integer sequenceIndex = entry.getKey();
            Supplier<String> assertMessageSupplier = () -> "Failed asserting event [" + sequenceIndex + "]";
            assertTrue(resultIterator.hasNext(), assertMessageSupplier);

            GenericDeadLetter<EventMessage> expected = entry.getValue();
            DeadLetter<? extends EventMessage> result = resultIterator.next();
            assertTrue(result instanceof JdbcDeadLetter);
            JdbcDeadLetter<? extends EventMessage> actual = ((JdbcDeadLetter<? extends EventMessage>) result);

            assertEquals(expected.getSequenceIdentifier(), actual.getSequenceIdentifier(), assertMessageSupplier);
            assertFalse(result.cause().isPresent(), assertMessageSupplier);
            assertEquals(expected.diagnostics(), actual.diagnostics(), assertMessageSupplier);
            assertEquals(sequenceIndex.longValue(), actual.getSequenceIndex(), assertMessageSupplier);
        }
    }

    private void insertLetterAtIndex(String aggregateId, DeadLetter<EventMessage> letter, int index) {
        joinAndUnwrap(executor.accept(connection -> {
            executeUpdate(
                    connection,
                    c -> statementFactory.enqueueStatement(
                            c, TEST_PROCESSING_GROUP, aggregateId, letter, index, null
                    ),
                    e -> new JdbcException(
                            "Failed to enqueue dead letter with message id [" +
                                    letter.message().identifier() + "] during testing", e
                    )
            );
        }));
    }
}
