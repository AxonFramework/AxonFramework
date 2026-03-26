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

import org.axonframework.common.jdbc.ConnectionExecutor;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdate;
import static org.axonframework.messaging.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the
 * {@link JdbcSequencedDeadLetterQueue} with an {@link EventProcessor} and {@code DeadLetteringEventHandlingComponent}.
 *
 * @author Steven van Beelen
 */
class JdbcDeadLetteringEventIntegrationTest extends DeadLetteringEventIntegrationTest {

    private DataSource dataSource;
    private JdbcTransactionalExecutorProvider executorProvider;
    private DeadLetterStatementFactory<EventMessage> statementFactory;
    private JdbcSequencedDeadLetterQueue<EventMessage> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
    private final JacksonConverter jacksonConverter = new JacksonConverter();
    private final EventConverter eventConverter = new DelegatingEventConverter(jacksonConverter);

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildDeadLetterQueue() {
        dataSource = dataSource();
        executorProvider = new JdbcTransactionalExecutorProvider(dataSource);

        Converter genericConverter = jacksonConverter;
        statementFactory = DefaultDeadLetterStatementFactory.builder()
                                                            .eventConverter(eventConverter)
                                                            .genericConverter(genericConverter)
                                                            .schema(schema)
                                                            .build();

        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.builder()
                                                          .processingGroup(PROCESSING_GROUP)
                                                          .transactionalExecutorProvider(executorProvider)
                                                          .schema(schema)
                                                          .statementFactory(statementFactory)
                                                          .eventConverter(eventConverter)
                                                          .genericConverter(genericConverter)
                                                          .build();
        return jdbcDeadLetterQueue;
    }

    private static DataSource dataSource() {
        var dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:" + JdbcDeadLetteringEventIntegrationTest.class.getSimpleName());
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    /**
     * Builds a {@link UnitOfWorkFactory} that registers a
     * {@link JdbcTransactionalExecutorProvider#SUPPLIER_KEY SUPPLIER_KEY} resource on each
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}, similar to how
     * {@code org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager} populates this resource
     * in production.
     * <p>
     * This allows the {@link JdbcTransactionalExecutorProvider} to extract the {@link ConnectionExecutor} from the
     * context when the {@link EventProcessor} processes events, exercising the same code path as production.
     */
    @Override
    protected UnitOfWorkFactory buildUnitOfWorkFactory() {
        return new SimpleUnitOfWorkFactory(
                EmptyApplicationContext.INSTANCE,
                config -> config.registerProcessingLifecycleEnhancer(
                        processingLifecycle ->
                                processingLifecycle.runOnPreInvocation(pc -> pc.putResource(
                                                                               JdbcTransactionalExecutorProvider.SUPPLIER_KEY,
                                                                               CachingSupplier.of(() -> new ConnectionExecutor(dataSource::getConnection))
                                                                       )
                                )
                )
        );
    }

    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    @BeforeEach
    void setUpJdbc() {
        joinAndUnwrap(
                executorProvider.getTransactionalExecutor(null)
                                .accept(connection -> connection.prepareStatement(
                                                "DROP TABLE IF EXISTS " + schema.deadLetterTable()
                                        ).executeUpdate()
                                )
        );
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
            assertEquals(expected.message().payload(),
                         actual.message().payloadAs(Integer.class),
                         assertMessageSupplier);
            assertFalse(result.cause().isPresent(), assertMessageSupplier);
            assertEquals(expected.diagnostics(), actual.diagnostics(), assertMessageSupplier);
            assertEquals(sequenceIndex.longValue(), actual.getSequenceIndex(), assertMessageSupplier);
        }
    }

    private void insertLetterAtIndex(String aggregateId, DeadLetter<EventMessage> letter, int index) {
        joinAndUnwrap(
                executorProvider.getTransactionalExecutor(null)
                                .accept(connection -> executeUpdate(
                                                connection,
                                                c -> statementFactory.enqueueStatement(
                                                        c,
                                                        PROCESSING_GROUP,
                                                        aggregateId,
                                                        letter,
                                                        index
                                                ),
                                                e -> new JdbcException(
                                                        "Failed to enqueue dead letter with message id ["
                                                                +
                                                                letter.message()
                                                                      .identifier()
                                                                + "] during testing",
                                                        e
                                                )
                                        )
                                )
        );
    }
}
