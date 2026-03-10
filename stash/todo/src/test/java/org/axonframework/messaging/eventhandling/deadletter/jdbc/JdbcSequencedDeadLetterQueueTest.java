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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jdbc.ConnectionExecutor;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionalExecutorProvider;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterWithContext;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.DateTimeUtils.parseInstant;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdates;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Implementation of the {@link SequencedDeadLetterQueueTest}, validating the {@link JdbcSequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
@Tag("flaky")
class JdbcSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;
    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    private DataSource dataSource;
    private TransactionalExecutorProvider<Connection> executorProvider;
    private JdbcSequencedDeadLetterQueue<EventMessage> jdbcDeadLetterQueue;
    private final JacksonConverter jacksonConverter = new JacksonConverter();
    private final DelegatingEventConverter eventConverter = new DelegatingEventConverter(jacksonConverter);
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildTestSubject() {
        dataSource = dataSource();
        executorProvider = new JdbcTransactionalExecutorProvider(dataSource);

        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                                          .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                                          .transactionalExecutorProvider(executorProvider)
                                                          .schema(schema)
                                                          .eventConverter(eventConverter)
                                                          .genericConverter(jacksonConverter)
                                                          .build();
        return jdbcDeadLetterQueue;
    }

    private static JDBCDataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:" + JdbcSequencedDeadLetterQueueTest.class.getSimpleName());
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    @BeforeEach
    void setUpJdbc() {
        dropDeadLetterTable(dataSource);
        joinAndUnwrap(jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory(), null));
    }

    private void dropDeadLetterTable(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            executeUpdates(
                    connection,
                    e -> {
                        throw new JdbcException("Unable to prepare dead letter table", e);
                    },
                    c -> c.prepareStatement("DROP TABLE IF EXISTS " + schema.deadLetterTable())
            );
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve a Connection to prepare the dead letter table", e);
        } finally {
            closeQuietly(connection);
        }
    }

    @Override
    protected long maxSequences() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    protected long maxSequenceSize() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    public DeadLetterWithContext<EventMessage> generateInitialLetter() {
        return new DeadLetterWithContext<>(
                new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable()),
                buildTestContext()
        );
    }

    @Override
    protected DeadLetterWithContext<EventMessage> generateFollowUpLetter() {
        return new DeadLetterWithContext<>(
                new GenericDeadLetter<>("sequenceIdentifier", generateEvent()),
                buildTestContext()
        );
    }

    private Context buildTestContext() {
        long seqNo = sequenceCounter.getAndIncrement();
        return Context.with(TrackingToken.RESOURCE_KEY, new GlobalSequenceTrackingToken(seqNo))
                      .withResource(LegacyResources.AGGREGATE_TYPE_KEY, "TestAggregate")
                      .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, "aggregate-" + seqNo)
                      .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, seqNo);
    }

    @Override
    protected DeadLetter<EventMessage> mapToQueueImplementation(DeadLetterWithContext<EventMessage> letterWithContext) {
        DeadLetter<EventMessage> deadLetter = letterWithContext.letter();
        if (deadLetter instanceof JdbcDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            return new JdbcDeadLetter<>(
                    IdentifierFactory.getInstance().generateIdentifier(),
                    0L,
                    ((GenericDeadLetter<EventMessage>) deadLetter).getSequenceIdentifier().toString(),
                    deadLetter.enqueuedAt(),
                    deadLetter.lastTouched(),
                    deadLetter.cause().orElse(null),
                    deadLetter.diagnostics(),
                    deadLetter.message(),
                    null
            );
        }
        throw new IllegalArgumentException("Can not map dead letter of type " + deadLetter.getClass().getName());
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Override
    protected ProcessingContext toProcessingContext(Context context) {
        ProcessingContext pc = super.toProcessingContext(context);
        if (pc != null) {
            pc.putResource(JdbcTransactionalExecutorProvider.SUPPLIER_KEY,
                           CachingSupplier.of(() -> new ConnectionExecutor(dataSource::getConnection)));
        }
        return pc;
    }

    @Override
    protected Context extractContext(DeadLetter<? extends EventMessage> deadLetter) {
        if (deadLetter instanceof JdbcDeadLetter<? extends EventMessage> jdbcDeadLetter) {
            return jdbcDeadLetter.context();
        }
        return super.extractContext(deadLetter);
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage> expected,
                                DeadLetter<? extends EventMessage> actual) {
        assertMessage(expected.message(), actual.message());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(formatExpected(expected.enqueuedAt()), actual.enqueuedAt());
        assertEquals(formatExpected(expected.lastTouched()), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }

    @Override
    protected void assertMessage(EventMessage expected, EventMessage actual) {
        assertEquals(expected.identifier(), actual.identifier());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.metadata(), actual.metadata());

        // Payload is stored as raw bytes; deserialize to compare with the original
        Object deserializedPayload = eventConverter.convertPayload(actual, expected.payloadType());
        assertEquals(expected.payload(), deserializedPayload);
    }

    /**
     * Format the expected {@link java.time.Instant} to align with the precision as dictated by the
     * {@link org.axonframework.common.DateTimeUtils}. Required as the actual {@code Instants} underwent formatting by
     * the {@link JdbcSequencedDeadLetterQueue} as well, whereas the {@code expected} value did not.
     */
    private static java.time.Instant formatExpected(java.time.Instant expected) {
        return parseInstant(formatInstant(expected));
    }

    @Test
    void invokingEvictWithNonJdbcDeadLetterThrowsWrongDeadLetterTypeException() {
        DeadLetter<EventMessage> testLetter = generateInitialLetter().letter();
        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> jdbcDeadLetterQueue.evict(testLetter, null).join());
        assertInstanceOf(WrongDeadLetterTypeException.class, exception.getCause());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullProcessingGroupThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.processingGroup(null));
    }

    @Test
    void buildWithEmptyProcessingGroupThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.processingGroup(""));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullTransactionalExecutorProviderThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.transactionalExecutorProvider(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullSchemaThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.schema(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullStatementFactoryThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.statementFactory(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.converter(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullEventConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.eventConverter(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullGenericConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.genericConverter(null));
    }

    @Test
    void buildWithZeroMaxSequencesThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.maxSequences(0));
    }

    @Test
    void buildWithNegativeMaxSequencesThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.maxSequences(-1));
    }

    @Test
    void buildWithZeroMaxSequenceSizeThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.maxSequenceSize(0));
    }

    @Test
    void buildWithNegativeMaxSequenceSizeThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.maxSequenceSize(-1));
    }

    @Test
    void buildWithNullClaimDurationThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.claimDuration(null));
    }

    @Test
    void buildWithZeroPageSizeThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.pageSize(0));
    }

    @Test
    void buildWithNegativePageSizeThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> testBuilder.pageSize(-1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildWithoutProcessingGroupThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .transactionalExecutorProvider(executorProvider)
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .converter(mock(DeadLetterJdbcConverter.class));

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildWithoutTransactionalExecutorProviderThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .converter(mock(DeadLetterJdbcConverter.class));

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildWithoutStatementFactoryAndGenericConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .transactionalExecutorProvider(executorProvider)
                                            .converter(mock(DeadLetterJdbcConverter.class))
                                            .eventConverter(eventConverter);

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildWithoutStatementFactoryAndEventConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .transactionalExecutorProvider(executorProvider)
                                            .converter(mock(DeadLetterJdbcConverter.class))
                                            .genericConverter(jacksonConverter);

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildWithoutConverterAndGenericConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .transactionalExecutorProvider(executorProvider)
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .eventConverter(eventConverter);

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildWithoutConverterAndEventConverterThrowsAxonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .transactionalExecutorProvider(executorProvider)
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .genericConverter(jacksonConverter);

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }
}
