package org.axonframework.eventstore.jpa;

import org.junit.*;
import org.mockito.*;

import javax.sql.DataSource;
import javax.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Martin Tilma
 */
public class SQLErrorCodesResolverTest {

    @Test
    public void testIsDuplicateKey() throws Exception {
        SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesResolver();

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKey(new PersistenceException("error",
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

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKey(new PersistenceException("error",
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

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKey(new PersistenceException("error",                                                                                      sqlException));

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

        boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKey(new PersistenceException("error",                                                                                      sqlException));

        assertFalse(isDuplicateKey);
    }
}
