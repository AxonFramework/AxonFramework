/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Clock;
import javax.sql.DataSource;

/**
 * Implementation of the {@link SequencedDeadLetterQueueTest}, validating the {@link JdbcSequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class JdbcSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;
    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        DataSource dataSource = dataSource();
        JdbcSequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                            .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                            .connectionProvider(dataSource::getConnection)
                                            .transactionManager(transactionManager(dataSource))
                                            .serializer(TestSerializer.JACKSON.getSerializer())
                                            .build();
        deadLetterQueue.createSchema(new GenericDeadLetterTableFactory());
        return deadLetterQueue;
    }

    private DataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:axontest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private TransactionManager transactionManager(DataSource dataSource) {
        PlatformTransactionManager platformTransactionManager = platformTransactionManager(dataSource);
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

    private PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
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
    public DeadLetter<EventMessage<?>> generateInitialLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateFollowUpLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }
}
