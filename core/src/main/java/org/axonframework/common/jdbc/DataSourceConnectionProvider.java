package org.axonframework.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * @author Allard Buijze
 */
public class DataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    public DataSourceConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
