package org.axonframework.common.jdbc;

import org.springframework.jdbc.datasource.DataSourceUtils;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * ConnectionProvider implementation that is aware of Transaction Managers and provides the connection attached to an
 * active transaction manager, instead of asking a Data Source directly.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class SpringDataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;
    private final SpringConnectionCloseHandler closeHandler;

    /**
     * Initialize the connection provider, using given <code>dataSource</code> to obtain a connection, when required.
     *
     * @param dataSource The data source to obtain connections from, when required
     */
    public SpringDataSourceConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
        this.closeHandler = new SpringConnectionCloseHandler(dataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        final Connection connection = DataSourceUtils.doGetConnection(dataSource);
        return ConnectionWrapperFactory.wrap(connection, closeHandler);
    }

    private static class SpringConnectionCloseHandler implements ConnectionWrapperFactory.ConnectionCloseHandler {

        private final DataSource dataSource;

        public SpringConnectionCloseHandler(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void close(Connection delegate) {
            DataSourceUtils.releaseConnection(delegate, dataSource);
        }
    }
}
