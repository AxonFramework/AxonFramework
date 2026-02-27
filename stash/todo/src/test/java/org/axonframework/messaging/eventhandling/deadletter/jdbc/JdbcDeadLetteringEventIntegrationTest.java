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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdate;
import static org.axonframework.messaging.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link JdbcSequencedDeadLetterQueue} with an {@link EventProcessor} and
 * {@code DeadLetteringEventHandlingComponent}.
 *
 * @author Steven van Beelen
 */
class JdbcDeadLetteringEventIntegrationTest extends DeadLetteringEventIntegrationTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    private JDBCDataSource dataSource;
    private JdbcTransactionalExecutorProvider executorProvider;
    private DeadLetterStatementFactory<EventMessage> statementFactory;
    private JdbcSequencedDeadLetterQueue<EventMessage> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
    private final JacksonConverter jacksonConverter = new JacksonConverter();
    private final EventConverter eventConverter = new DelegatingEventConverter(jacksonConverter);

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:dlqintegtest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        executorProvider = testTransactionalExecutorProvider();

        Converter genericConverter = jacksonConverter;
        statementFactory = DefaultDeadLetterStatementFactory.<EventMessage>builder()
                                                            .eventConverter(eventConverter)
                                                            .genericConverter(genericConverter)
                                                            .schema(schema)
                                                            .build();

        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.<EventMessage>builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .transactionalExecutorProvider(executorProvider)
                                                          .schema(schema)
                                                          .statementFactory(statementFactory)
                                                          .eventConverter(eventConverter)
                                                          .genericConverter(genericConverter)
                                                          .build();
        return jdbcDeadLetterQueue;
    }

    /**
     * Creates a {@link JdbcTransactionalExecutorProvider} that always uses the null-context code path,
     * which creates a new {@link Connection} from the {@link javax.sql.DataSource} with its own transaction
     * for each operation.
     * <p>
     * This is necessary because the integration test runs a real {@link EventProcessor} whose
     * {@link ProcessingContext} instances do not carry the
     * {@link JdbcTransactionalExecutorProvider#SUPPLIER_KEY SUPPLIER_KEY} resource. The default
     * {@link JdbcTransactionalExecutorProvider} would throw when it receives a non-null context
     * without that resource. Overriding {@code getTransactionalExecutor} to ignore the context
     * avoids this, while still exercising the production {@link JdbcTransactionalExecutorProvider}
     * connection and transaction management logic.
     */
    private JdbcTransactionalExecutorProvider testTransactionalExecutorProvider() {
        return new JdbcTransactionalExecutorProvider(dataSource) {
            @Override
            @Nonnull
            public TransactionalExecutor<Connection> getTransactionalExecutor(@Nullable ProcessingContext processingContext) {
                return super.getTransactionalExecutor(null);
            }
        };
    }

    @Override
    protected Converter converter() {
        return jacksonConverter;
    }

    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    @BeforeEach
    void setUpJdbc() {
        joinAndUnwrap(executorProvider.getTransactionalExecutor(null).accept(connection ->
                connection.prepareStatement("DROP TABLE IF EXISTS " + schema.deadLetterTable()).executeUpdate()
        ));
        joinAndUnwrap(jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory(), null));
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
        joinAndUnwrap(executorProvider.getTransactionalExecutor(null).accept(connection ->
                executeUpdate(
                        connection,
                        c -> statementFactory.enqueueStatement(
                                c, TEST_PROCESSING_GROUP, aggregateId, letter, index, null
                        ),
                        e -> new JdbcException(
                                "Failed to enqueue dead letter with message id [" +
                                        letter.message().identifier() + "] during testing", e
                        )
                )
        ));
    }
}
