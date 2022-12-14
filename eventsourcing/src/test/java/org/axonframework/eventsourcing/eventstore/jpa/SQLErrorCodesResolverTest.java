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

package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.persistence.PersistenceException;
import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Martin Tilma
 */
class SQLErrorCodesResolverTest {

    @Test
    void isDuplicateKey() {
        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(new ArrayList<>());

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        new RuntimeException()));

        assertFalse(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingSetDuplicateKeyCodes() {
        List<Integer> errorCodes = new ArrayList<>();
        errorCodes.add(-104);
        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(errorCodes);

        SQLException sqlException = new SQLException("test", "error", errorCodes.get(0));

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
    }


    @Test
    void isDuplicateKey_isDuplicateKey_usingDataSource() throws Exception {
        String databaseProductName = "HSQL Database Engine";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(dataSource);

        SQLException sqlException = new SQLException("test", "error", -104);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingAS400DataSource() throws Exception {
        String databaseProductName = "DB2 UDB for AS/400";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(dataSource);

        SQLException sqlException = new SQLException("test", "error", -803);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingDB2LinuxDataSource() throws Exception {
        String databaseProductName = "DB2/LINUXX8664";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(dataSource);

        SQLException sqlException = new SQLException("test", "error", -803);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingRandomDb2DataSource() throws Exception {
        String databaseProductName = "DB2 Completely unexpected value";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(dataSource);

        SQLException sqlException = new SQLException("test", "error", -803);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingProductName() {
        String databaseProductName = "HSQL Database Engine";

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(databaseProductName);

        SQLException sqlException = new SQLException("test", "error", -104);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingCustomProperties() throws Exception {
        Properties props = new Properties();
        props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

        String databaseProductName = "MyCustom Database Engine";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(props, dataSource);

        SQLException sqlException = new SQLException("test", "error", -104);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void initialization_UnknownProductName() throws Exception {
        DataSource dataSource = createMockDataSource("Some weird unknown DB type");
        assertThrows(AxonConfigurationException.class, () -> new SQLErrorCodesResolver(dataSource))
        ;
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingSqlState() {
        Properties props = new Properties();
        props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

        String databaseProductName = "MyCustom Database Engine";

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(props, databaseProductName);

        SQLException sqlException = new SQLException("test", "-104");

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingNonIntSqlState() {
        Properties props = new Properties();
        props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

        String databaseProductName = "MyCustom Database Engine";

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(props, databaseProductName);

        SQLException sqlException = new SQLException("test", "thisIsNotAnInt");

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertFalse(isDuplicateKey);
    }

    @Test
    void isDuplicateKey_isDuplicateKey_usingNonNullSqlState() {
        Properties props = new Properties();
        props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

        String databaseProductName = "MyCustom Database Engine";

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver(props, databaseProductName);

        SQLException sqlException = new SQLException("test", (String) null);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertFalse(isDuplicateKey);
    }

    private DataSource createMockDataSource(String databaseProductName) throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData databaseMetaData = Mockito.mock(DatabaseMetaData.class);

        Mockito.when(databaseMetaData.getDatabaseProductName()).thenReturn(databaseProductName);
        Mockito.when(connection.getMetaData()).thenReturn(databaseMetaData);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
