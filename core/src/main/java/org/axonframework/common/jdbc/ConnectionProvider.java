package org.axonframework.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface towards a mechanism that provides access to a JDBC Connection.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public interface ConnectionProvider {

    /**
     * Returns a connection, ready for use.
     *
     * @return a new connection to use
     *
     * @throws SQLException when an error occurs obtaining the connection
     */
    Connection getConnection() throws SQLException;
}
