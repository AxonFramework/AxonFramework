package org.axonframework.common.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;

import org.junit.Test;

/**
 * Tests the {@link JdbcUtils} class static methods
 * 
 * @author Albert Attard (Java Creed)
 * @see JdbcUtils
 */
public class JdbcUtilsTest {

    /**
     * Tries to read from an empty result set. The method is expected to return
     * {@code null}
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    public void testNextAndExtract_EmptyResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(false);
        assertNull(JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads {@code null} from the result set. The result set here returns
     * {@code null}.
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    public void testNextAndExtract_NullValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(null);
        assertNull(JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads a value from the results set. The method should return the value that
     * was read.
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    public void testNextAndExtract_NonNullValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(10L);
        assertEquals(Long.valueOf(10L), JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads a null value but the result set returns 0L while the wasNull() method
     * returns false, which indicates that the value read from the result set was
     * actually {@code null}.
     * 
     * This test was added to address issue
     * <a href="https://github.com/AxonFramework/AxonFramework/issues/636">#638</a>
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    public void testNextAndExtract_NonNullValue_WasNull() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);
        assertNull(JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }
}
