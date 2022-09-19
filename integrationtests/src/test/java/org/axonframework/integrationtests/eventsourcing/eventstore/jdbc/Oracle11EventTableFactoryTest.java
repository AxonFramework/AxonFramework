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

package org.axonframework.integrationtests.eventsourcing.eventstore.jdbc;

import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.Oracle11EventTableFactory;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.axonframework.common.io.IOUtils.closeQuietly;

/**
 * Integration test class validating the {@link Oracle11EventTableFactory}.
 *
 * @author Joris van der Kallen
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
@Testcontainers
class Oracle11EventTableFactoryTest {

    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    @Container
    private static final OracleContainer ORACLE_CONTAINER = new OracleContainer("gvenzl/oracle-xe");

    private Oracle11EventTableFactory testSubject;
    private Connection connection;
    private EventSchema eventSchema;

    @BeforeEach
    void setUp() throws SQLException {
        testSubject = new Oracle11EventTableFactory();
        eventSchema = new EventSchema();
        Properties properties = new Properties();
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        //Disable oracle.jdbc.timezoneAsRegion as when on true GHA fails to run this test due to missing region-info
        properties.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        connection = DriverManager.getConnection(ORACLE_CONTAINER.getJdbcUrl(), properties);
    }

    @AfterEach
    void tearDown() {
        closeQuietly(connection);
    }

    @Test
    void createDomainEventTable() throws Exception {
        // test passes if no exception is thrown
        testSubject.createDomainEventTable(connection, eventSchema)
                   .execute();
        connection.prepareStatement("SELECT * FROM " + eventSchema.domainEventTable())
                  .execute();

        connection.prepareStatement("DROP TABLE " + eventSchema.domainEventTable())
                  .execute();
        connection.prepareStatement("DROP SEQUENCE " + eventSchema.domainEventTable() + "_seq")
                  .execute();
    }

    @Test
    void createSnapshotEventTable() throws Exception {
        // test passes if no exception is thrown
        testSubject.createSnapshotEventTable(connection, eventSchema)
                   .execute();
        connection.prepareStatement("SELECT * FROM " + eventSchema.snapshotTable())
                  .execute();

        connection.prepareStatement("DROP TABLE " + eventSchema.snapshotTable())
                  .execute();
    }
}
