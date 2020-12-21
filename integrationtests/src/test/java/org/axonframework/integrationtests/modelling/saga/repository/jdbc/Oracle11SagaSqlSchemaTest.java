/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.integrationtests.modelling.saga.repository.jdbc;

import org.axonframework.modelling.saga.repository.jdbc.Oracle11SagaSqlSchema;
import org.axonframework.modelling.saga.repository.jdbc.SagaSchema;
import org.junit.jupiter.api.*;
import org.opentest4j.TestAbortedException;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;

/**
 * Integration test class validating the {@link Oracle11SagaSqlSchema}.
 *
 * @author Joris van der Kallen
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
@Testcontainers
class Oracle11SagaSqlSchemaTest {

    private static final String USERNAME = "system";
    private static final String PASSWORD = "oracle";

    @Container
    private static final OracleContainer ORACLE_CONTAINER = new OracleContainer("gautamsaggar/oracle11g:v2");

    private Oracle11SagaSqlSchema testSubject;
    private Connection connection;
    private SagaSchema sagaSchema;

    @BeforeEach
    void setUp() {
        sagaSchema = new SagaSchema();
        testSubject = new Oracle11SagaSqlSchema(sagaSchema);
        try {
            connection = DriverManager.getConnection(ORACLE_CONTAINER.getJdbcUrl(), USERNAME, PASSWORD);
        } catch (SQLException e) {
            throw new TestAbortedException(
                    "Ignoring test. Machine does not have a local Oracle 11 instance running", e
            );
        }
    }

    @AfterEach
    void tearDown() {
        closeQuietly(connection);
    }

    @Test
    void testSql_createTableAssocValueEntry() throws Exception {
        // test passes if no exception is thrown
        testSubject.sql_createTableAssocValueEntry(connection)
                   .execute();
        connection.prepareStatement("SELECT * FROM " + sagaSchema.associationValueEntryTable())
                  .execute();

        connection.prepareStatement("DROP TABLE " + sagaSchema.associationValueEntryTable())
                  .execute();
        connection.prepareStatement("DROP SEQUENCE " + sagaSchema.associationValueEntryTable() + "_seq")
                  .execute();
    }

    @Test
    void testSql_createTableSagaEntry() throws Exception {
        // test passes if no exception is thrown
        testSubject.sql_createTableSagaEntry(connection)
                   .execute();
        connection.prepareStatement("SELECT * FROM " + sagaSchema.sagaEntryTable())
                  .execute();

        connection.prepareStatement("DROP TABLE " + sagaSchema.sagaEntryTable())
                  .execute();
    }
}
