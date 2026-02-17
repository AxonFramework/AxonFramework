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

import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.eventhandling.deadletter.PublisherTestUtils;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdate;
import static org.axonframework.common.jdbc.JdbcUtils.executeUpdates;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
class PagingJdbcPublisherTest {

    private DataSource dataSource;
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        dataSource = dataSource();
        transactionManager = transactionManager(dataSource);
        transactionManager.executeInTransaction(() -> {
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                executeUpdates(
                        connection,
                        e -> {
                            throw new JdbcException("Enable to prepare test_table", e);
                        },
                        c -> c.prepareStatement("DROP TABLE IF EXISTS test_table"),
                        c -> c.prepareStatement(
                                "CREATE TABLE IF NOT EXISTS test_table ("
                                        + "identifier VARCHAR(255) NOT NULL,"
                                        + "idIndex BIGINT NOT NULL"
                                        + ")"
                        )
                );
            } catch (SQLException e) {
                throw new IllegalStateException("Enable to retrieve a Connection to prepare the test_table", e);
            } finally {
                closeQuietly(connection);
            }
        });
    }

    @Test
    void publishesJustOneItemAsOnePage() {
        String testId = IdentifierFactory.getInstance().generateIdentifier();
        addEntryAt(testId, 1);

        List<String> result = PublisherTestUtils.collect(createPublisher());

        assertEquals(List.of(testId), result);
    }

    @Test
    void publishesMultiplePagesInOrder() {
        List<String> expectedIds = IntStream.range(0, 102)
                                            .mapToObj(i -> "%03d".formatted(i))
                                            .toList();
        expectedIds.forEach(expectedId -> addEntryAt(expectedId, Long.parseLong(expectedId)));

        List<String> result = PublisherTestUtils.collect(createPublisher());

        assertEquals(expectedIds, result);
    }

    @Test
    void respectsBackpressureAndCancellation() throws InterruptedException {
        IntStream.range(0, 20)
                 .mapToObj(i -> "%03d".formatted(i))
                 .forEach(id -> addEntryAt(id, Long.parseLong(id)));

        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        List<String> result = new ArrayList<>();
        CountDownLatch receivedFive = new CountDownLatch(1);

        createPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(String item) {
                result.add(item);
                if (result.size() == 5) {
                    subscriptionRef.get().cancel();
                    receivedFive.countDown();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                receivedFive.countDown();
            }

            @Override
            public void onComplete() {
                completed.set(true);
                receivedFive.countDown();
            }
        });

        Flow.Subscription subscription = subscriptionRef.get();
        assertNotNull(subscription);
        subscription.request(5);

        assertTrue(receivedFive.await(1, TimeUnit.SECONDS));
        assertEquals(5, result.size());
        assertFalse(completed.get());
    }

    @Test
    void emitsErrorWhenRequestingNonPositiveDemand() throws InterruptedException {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        createPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(0);
            }

            @Override
            public void onNext(String item) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(errorRef.get() instanceof IllegalArgumentException);
    }

    @Test
    void emitsErrorWhenQueryFails() throws InterruptedException {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        PagingJdbcPublisher<String> publisher = new PagingJdbcPublisher<>(
                Runnable::run,
                transactionManager,
                () -> {
                    try {
                        return dataSource.getConnection();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                (connection, offset, maxSize) -> {
                    throw new SQLException("query-failed");
                },
                10,
                resultSet -> resultSet.getString("identifier"),
                RuntimeException::new
        );

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                // no-op
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertTrue(errorRef.get() instanceof RuntimeException);
    }

    private PagingJdbcPublisher<String> createPublisher() {
        return new PagingJdbcPublisher<>(
                Runnable::run,
                transactionManager,
                () -> {
                    try {
                        return dataSource.getConnection();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                (connection, offset, maxSize) -> {
                    String sql = "SELECT * FROM test_table WHERE idIndex >=? ORDER BY idIndex LIMIT ?";
                    PreparedStatement statement = connection.prepareStatement(sql);
                    statement.setLong(1, offset);
                    statement.setLong(2, maxSize);
                    return statement;
                },
                10,
                resultSet -> resultSet.getString("identifier"),
                RuntimeException::new
        );
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

    private void addEntryAt(String id, long index) {
        transactionManager.executeInTransaction(() -> {
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                executeUpdate(
                        connection,
                        c -> {
                            String sql = "INSERT INTO test_table (identifier, idIndex) VALUES(?,?)";
                            PreparedStatement statement = c.prepareStatement(sql);
                            statement.setString(1, id);
                            statement.setLong(2, index);
                            return statement;
                        },
                        e -> new JdbcException("Enable to insert entry [" + id + "] at index [" + index + "]", e)
                );
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "Enable to retrieve a Connection to insert entry [" + id + "] at index [" + index + "]", e
                );
            } finally {
                closeQuietly(connection);
            }
        });
    }
}
