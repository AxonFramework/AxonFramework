/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.common.io;

import org.joda.time.DateTime;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kristian Rosenvold
 */
public class JdbcUtils {
    public static void closeAllQuietly(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                closeAllQuietly(resultSet.getStatement());
            } catch (SQLException ignore) {
            }
        }
    }

    public static void closeAllQuietly(Statement statement) {
        if (statement != null) {
            try {
                closeQuietly(statement.getConnection());
                statement.close();
            } catch (SQLException ignore) {
            }
        }
    }

	private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignore) {
            }
        }
    }

    public static PreparedStatement createPreparedStatement(String sql, Connection connection) {
        try {
            return connection.prepareStatement(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static PreparedStatement createPreparedStatement(String sql, Connection connection, Object... params) {
        PreparedStatement preparedStatement;
        try {
            preparedStatement = createPreparedStatement(sql, connection);
            setParams(preparedStatement, params);
            return preparedStatement;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static int executeUpdate(PreparedStatement preparedStatement) {
        try {
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            closeAllQuietly(preparedStatement);
        }
    }

    public static boolean execute(PreparedStatement preparedStatement) {
        try {
            return preparedStatement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            closeAllQuietly(preparedStatement);
        }
    }

    private static void setParams(PreparedStatement preparedStatement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object x = params[i];
            if (x instanceof DateTime) x = new Timestamp(((DateTime) x).getMillis());
            if (x instanceof byte[]) {
                preparedStatement.setBytes(i + 1, (byte[]) x);
            } else {
                preparedStatement.setObject(i + 1, x);
            }
        }
    }

    public static ResultSet executeQuery(PreparedStatement preparedStatement) {
        try {
            return preparedStatement.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

	public static Connection getConnection(DataSource dataSource){
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection getNonAutoCommittConnection(DataSource dataSource){
        try {
            final Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

	public static ResultSet executeBatchingQuery(PreparedStatement preparedStatement, int fetchSize) {
        try {
            preparedStatement.setFetchSize(fetchSize);
            return preparedStatement.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static void closeBatchingPreparedStatement(ResultSet rs) {
        try {
            Statement statement = rs.getStatement();
            Connection connection = safeGetConnection(statement);
            if (connection != null) {
                connection.setAutoCommit(true);
            }
            closeAllQuietly(statement);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Connection safeGetConnection(Statement statement) throws SQLException {
        if (statement == null) return null;
        return statement.getConnection();
    }


    public static interface ResultSetParser<T> {
        T createItem(ResultSet rs) throws SQLException;
    }

    public static <T> List<T> createList(ResultSet rs, ResultSetParser<T> factory) {
        List<T> result = new ArrayList<T>();
        try {
            while (rs.next()) {
                result.add(factory.createItem(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

}
