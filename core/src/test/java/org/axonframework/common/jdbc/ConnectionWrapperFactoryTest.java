package org.axonframework.common.jdbc;

import org.junit.*;

import java.sql.Connection;

import static org.axonframework.common.jdbc.ConnectionWrapperFactory.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ConnectionWrapperFactoryTest {

    private ConnectionWrapperFactory.ConnectionCloseHandler closeHandler;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        closeHandler = mock(ConnectionWrapperFactory.ConnectionCloseHandler.class);
        connection = mock(Connection.class);
    }

    @Test
    public void testWrapperDelegatesAllButClose() throws Exception {
        Connection wrapped = wrap(connection, closeHandler);
        wrapped.commit();
        verify(connection).commit();

        wrapped.getAutoCommit();
        verify(connection).getAutoCommit();

        verifyZeroInteractions(closeHandler);

        wrapped.close();
        verify(connection, never()).close();
        verify(closeHandler).close(connection);
    }

    @Test
    public void testEquals_WithWrapper() {
        final Runnable runnable = mock(Runnable.class);
        Connection wrapped = wrap(connection, Runnable.class, runnable, closeHandler);

        assertFalse(wrapped.equals(connection));
        assertTrue(wrapped.equals(wrapped));
    }

    @Test
    public void testEquals_WithoutWrapper() {
        Connection wrapped = wrap(connection, closeHandler);

        assertFalse(wrapped.equals(connection));
        assertTrue(wrapped.equals(wrapped));
    }

    @Test
    public void testHashCode_WithWrapper() throws Exception {
        final Runnable runnable = mock(Runnable.class);
        Connection wrapped = wrap(connection, Runnable.class, runnable, closeHandler);
        assertEquals(wrapped.hashCode(), wrapped.hashCode());
    }

    @Test
    public void testHashCode_WithoutWrapper() throws Exception {
        Connection wrapped = wrap(connection, closeHandler);
        assertEquals(wrapped.hashCode(), wrapped.hashCode());
    }
}
