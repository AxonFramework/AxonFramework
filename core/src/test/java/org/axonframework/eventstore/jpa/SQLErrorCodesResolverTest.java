/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.jpa;

import org.junit.*;
import org.mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.PersistenceException;
import javax.sql.DataSource;

import static org.junit.Assert.*;

/**
 * @author Martin Tilma
 */
public class SQLErrorCodesResolverTest {

    @Test
    public void testIsDuplicateKey() throws Exception {
        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver();

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        new RuntimeException()));

        assertFalse(isDuplicateKey);
    }

    @Test
    public void testIsDuplicateKey_isDuplicateKey_usingSetDuplicateKeyCodes() throws Exception {
        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver();

        List<Integer> errorCodes = new ArrayList<Integer>();
        errorCodes.add(-104);
        sqlErrorCodesResolver.setDuplicateKeyCodes(errorCodes);

        SQLException sqlException = new SQLException("test", "error", errorCodes.get(0));

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
    }


    @Test
    public void testIsDuplicateKey_isDuplicateKey_usingSetDataSource() throws Exception {
        String databaseProductName = "HSQL Database Engine";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver();
        sqlErrorCodesResolver.setDataSource(dataSource);

        SQLException sqlException = new SQLException("test", "error", -104);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertTrue(isDuplicateKey);
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

    @Test
    public void testIsDuplicateKey_isDuplicateKey_usingSetDataSource_unknownDatabaseProductName() throws Exception {
        String databaseProductName = "OOPS_DOES_NOT_EXISTS";
        DataSource dataSource = createMockDataSource(databaseProductName);

        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver();
        sqlErrorCodesResolver.setDataSource(dataSource);

        SQLException sqlException = new SQLException("test", "error", -104);

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
                                                                                                        sqlException));

        assertFalse(isDuplicateKey);
    }
}
