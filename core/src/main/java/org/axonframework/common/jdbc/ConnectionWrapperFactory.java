/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Factory for creating wrappers around a Connection, allowing one to override the behavior of the {@link
 * java.sql.Connection#close()} method.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public abstract class ConnectionWrapperFactory {

    private ConnectionWrapperFactory() {
    }

    /**
     * Wrap the given <code>connection</code>, creating a Proxy with an additional <code>wrapperInterface</code>
     * (implemented by given <code>wrapperHandler</code>). Calls to the close method are forwarded to the given
     * <code>closeHandler</code>.
     * <p/>
     * Note that all invocations on methods declared on the <code>wrapperInterface</code> (including equals, hashCode)
     * are forwarded to the <code>wrapperHandler</code>.
     *
     * @param connection       The connection to wrap
     * @param wrapperInterface The additional interface to implement
     * @param wrapperHandler   The implementation for the additional interface
     * @param closeHandler     The handler to redirect close invocations to
     * @param <I>              The type of additional interface for the wrapper to implement
     * @return a wrapped Connection
     */
    public static <I> Connection wrap(final Connection connection, final Class<I> wrapperInterface,
                                      final I wrapperHandler,
                                      final ConnectionCloseHandler closeHandler) {
        return (Connection) Proxy.newProxyInstance(wrapperInterface.getClassLoader(),
                                                   new Class[]{Connection.class, wrapperInterface},
                                                   new InvocationHandler() {
                                                       @Override
                                                       public Object invoke(Object proxy, Method method, Object[] args)
                                                               throws Throwable {
                                                           if ("equals".equals(method.getName()) && args != null
                                                                   && args.length == 1) {
                                                               return proxy == args[0];
                                                           } else if ("hashCode".equals(
                                                                   method.getName()) && isEmpty(args)) {
                                                               return connection.hashCode();
                                                           } else if (method.getDeclaringClass().isAssignableFrom(
                                                                   wrapperInterface)) {
                                                               return method.invoke(wrapperHandler, args);
                                                           } else if ("close".equals(method.getName())
                                                                   && isEmpty(args)) {
                                                               closeHandler.close(connection);
                                                               return null;
                                                           } else if ("commit".equals(method.getName())
                                                                   && isEmpty(args)) {
                                                               closeHandler.commit(connection);
                                                               return null;
                                                           } else {
                                                               return method.invoke(connection, args);
                                                           }
                                                       }
                                                   }
        );
    }

    /**
     * Wrap the given <code>connection</code>, creating a Proxy with an additional <code>wrapperInterface</code>
     * (implemented by given <code>wrapperHandler</code>). Calls to the close method are forwarded to the given
     * <code>closeHandler</code>.
     *
     * @param connection   The connection to wrap
     * @param closeHandler The handler to redirect close invocations to
     * @return a wrapped Connection
     */
    public static Connection wrap(final Connection connection, final ConnectionCloseHandler closeHandler) {
        return (Connection) Proxy.newProxyInstance(closeHandler.getClass().getClassLoader(),
                                                   new Class[]{Connection.class},
                                                   new InvocationHandler() {
                                                       @Override
                                                       public Object invoke(Object proxy, Method method, Object[] args)
                                                               throws Throwable {
                                                           if ("equals".equals(method.getName()) && args != null
                                                                   && args.length == 1) {
                                                               return proxy == args[0];
                                                           } else if ("hashCode".equals(
                                                                   method.getName()) && isEmpty(args)) {
                                                               return connection.hashCode();
                                                           } else if ("close".equals(method.getName())
                                                                   && isEmpty(args)) {
                                                               closeHandler.close(connection);
                                                               return null;
                                                           } else if ("commit".equals(method.getName())
                                                                   && isEmpty(args)) {
                                                               closeHandler.commit(connection);
                                                               return null;
                                                           } else {
                                                               return method.invoke(connection, args);
                                                           }
                                                       }
                                                   }
        );
    }

    private static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * Interface defining an operation to close the wrapped connection
     */
    public interface ConnectionCloseHandler {

        /**
         * Close the given <code>connection</code>, which was wrapped by the ConnectionWrapperFactory.
         *
         * @param connection the wrapped connection to close
         */
        void close(Connection connection);

        /**
         * Commits the underlying transaction
         *
         * @param connection the wrapped connection to commit
         * @throws java.sql.SQLException when an error occurs while committing the connection
         */
        void commit(Connection connection) throws SQLException;
    }

    /**
     * Implementation of ConnectionCloseHandler that does nothing on close.
     */
    public static class NoOpCloseHandler implements ConnectionCloseHandler {

        @Override
        public void close(Connection connection) {
        }

        @Override
        public void commit(Connection connection) throws SQLException {
            connection.commit();
        }
    }
}