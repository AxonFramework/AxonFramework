package org.axonframework.common.jdbc;

import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.axonframework.common.jdbc.ConnectionWrapperFactory.wrap;
import static org.junit.Assert.*;
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
        verify(closeHandler).commit(connection);

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

    @Test(expected = SQLException.class)
    public void testUnwrapInvocationTargetException() throws Exception {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException());

        Connection wrapper = wrap(connection, closeHandler);
        wrapper.prepareStatement("foo");
    }

    @Test(expected = SQLException.class)
    public void testUnwrapInvocationTargetExceptionWithAdditionalWrapperInterface1() throws Exception {
        WrapperInterface wrapperImplementation = mock(WrapperInterface.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException());

        Connection wrapper = wrap(connection, WrapperInterface.class, wrapperImplementation, closeHandler);
        wrapper.prepareStatement("foo");
    }

    @Test(expected = SQLException.class)
    public void testUnwrapInvocationTargetExceptionWithAdditionalWrapperInterface2() throws Exception {
        WrapperInterface wrapperImplementation = mock(WrapperInterface.class);
        doThrow(new SQLException()).when(wrapperImplementation).foo();

        WrapperInterface wrapper = (WrapperInterface) wrap(connection,
                                                           WrapperInterface.class,
                                                           wrapperImplementation,
                                                           closeHandler);
        wrapper.foo();
    }

    private interface WrapperInterface {

        void foo() throws SQLException;
    }
}