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
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;

import static org.axonframework.common.FutureUtils.joinAndUnwrap;
import static org.axonframework.common.jdbc.JdbcUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PagingJdbcIterable}.
 *
 * @author Steven van Beelen
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
class PagingJdbcIterableTest {

    private DataSource dataSource;
    // Sentinel connection to keep HSQLDB in-memory database alive across operations
    private Connection sentinelConnection;
    private TransactionalExecutor<Connection> executor;
    private PagingJdbcIterable<String> testSubject;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = dataSource();
        sentinelConnection = dataSource.getConnection();
        executor = new JdbcTransactionalExecutorProvider(dataSource).getTransactionalExecutor(null);

        // Use a direct connection for DDL setup to avoid HSQLDB issues with setAutoCommit(false) + DDL
        try (Connection setupConnection = dataSource.getConnection()) {
            executeUpdates(
                    setupConnection,
                    e -> {
                        throw new JdbcException("Unable to prepare test_table", e);
                    },
                    c -> c.prepareStatement("DROP TABLE IF EXISTS test_table"),
                    c -> c.prepareStatement(
                            "CREATE TABLE IF NOT EXISTS test_table ("
                                    + "identifier VARCHAR(255) NOT NULL,"
                                    + "idIndex BIGINT NOT NULL"
                                    + ")"
                    )
            );
        }

        testSubject = new PagingJdbcIterable<>(
                executor,
                (connection, offset, maxSize) -> {
                    String sql = "SELECT * FROM test_table WHERE idIndex >=? LIMIT ?";
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
        dataSource.setUrl("jdbc:hsqldb:mem:pagingtest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @AfterEach
    void tearDown() {
        if (sentinelConnection != null) {
            closeQuietly(sentinelConnection);
        }
    }

    @Test
    void queriesJustOneItemAsOnePage() {
        String testId = IdentifierFactory.getInstance().generateIdentifier();
        addEntryAt(testId, 1);

        List<String> result = StreamSupport.stream(testSubject.spliterator(), false)
                                           .collect(Collectors.toList());

        assertEquals(1, result.size());
        assertEquals(testId, result.get(0));
    }

    @Test
    void queriesMultiplePages() {
        List<String> expectedIds = IntStream.range(0, 102)
                                            .mapToObj(String::valueOf)
                                            .collect(Collectors.toList());
        expectedIds.forEach(expectedId -> addEntryAt(expectedId, Long.parseLong(expectedId)));

        List<String> result = StreamSupport.stream(testSubject.spliterator(), false)
                                           .collect(Collectors.toList());

        assertEquals(expectedIds.size(), result.size());
        expectedIds.forEach(resultId -> assertTrue(result.contains(resultId)));
    }

    private void addEntryAt(String id, long index) {
        joinAndUnwrap(executor.accept(connection -> {
            executeUpdate(
                    connection,
                    c -> {
                        String sql = "INSERT INTO test_table (identifier, idIndex) VALUES(?,?)";
                        PreparedStatement statement = c.prepareStatement(sql);
                        statement.setString(1, id);
                        statement.setLong(2, index);
                        return statement;
                    },
                    e -> new JdbcException("Unable to insert entry [" + id + "] at index [" + index + "]", e)
            );
        }));
    }

    @Test
    void throwsExceptionWhenNoItemPresent() {
        assertThrows(NoSuchElementException.class, testSubject.iterator()::next);
    }
}
