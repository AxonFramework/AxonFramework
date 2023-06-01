/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Utility class for safely executing Jdbc queries.
 *
 * @author Kristian Rosenvold
 * @author Allard Buijze
 * @author Rene de Waele
 * @since 2.2
 */
public class JdbcUtils {

    /**
     * Execute the query given by the {@code sqlFunction}. The {@link ResultSet} returned when the query is executed
     * will be converted using the given {@code sqlResultConverter}. Any errors will be handled by the given {@code
     * errorHandler}.
     *
     * @param connection         connection to the underlying database that should be used for the query
     * @param sqlFunction        the function that returns a {@link PreparedStatement} to execute the query against
     * @param sqlResultConverter converts the result set to a value of type R
     * @param errorHandler       handles errors as result of executing the query or converting the result set
     * @param <R>                the type result of the query
     * @return the query result  the result of the query
     */
    public static <R> R executeQuery(Connection connection, SqlFunction sqlFunction,
                                     SqlResultConverter<R> sqlResultConverter,
                                     Function<SQLException, RuntimeException> errorHandler) {
        return executeQuery(connection, sqlFunction, sqlResultConverter, errorHandler, true);
    }

    /**
     * Execute the query given by the {@code sqlFunction}. The {@link ResultSet} returned when the query is executed
     * will be converted using the given {@code sqlResultConverter}. Any errors will be handled by the given {@code
     * errorHandler}.
     *
     * @param connection         connection to the underlying database that should be used for the query
     * @param sqlFunction        the function that returns a {@link PreparedStatement} to execute the query against
     * @param sqlResultConverter converts the result set to a value of type R
     * @param errorHandler       handles errors as result of executing the query or converting the result set
     * @param closeConnection    whether provided {@code connection} should be closed or not
     * @param <R>                the type result of the query
     * @return the query result  the result of the query
     */
    public static <R> R executeQuery(Connection connection, SqlFunction sqlFunction,
                                     SqlResultConverter<R> sqlResultConverter,
                                     Function<SQLException, RuntimeException> errorHandler,
                                     boolean closeConnection) {
        try {
            PreparedStatement preparedStatement = createSqlStatement(connection, sqlFunction);
            try {
                ResultSet resultSet;
                try {
                    resultSet = preparedStatement.executeQuery();
                } catch (SQLException e) {
                    throw errorHandler.apply(e);
                }
                try {
                    return sqlResultConverter.apply(resultSet);
                } catch (SQLException e) {
                    throw errorHandler.apply(e);
                } finally {
                    closeQuietly(resultSet);
                }
            } finally {
                closeQuietly(preparedStatement);
            }
        } finally {
            if (closeConnection) {
                closeQuietly(connection);
            }
        }
    }

    /**
     * Execute the update statement produced by the given {@code updateFunction}. Any errors will be handled by the
     * given {@code errorHandler}.
     *
     * @param connection     connection to the underlying database that should be used for the update
     * @param updateFunction the function that produce the update statement
     * @param errorHandler   handles errors as result of executing the update
     * @return the update count resulting from the given {@code updateFunction}
     */
    public static int executeUpdate(Connection connection,
                                    SqlFunction updateFunction,
                                    Function<SQLException, RuntimeException> errorHandler) {
        PreparedStatement preparedStatement = createSqlStatement(connection, updateFunction);
        try {
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw errorHandler.apply(e);
        } finally {
            closeQuietly(preparedStatement);
        }
    }

    /**
     * Execute the update statements produced by the given {@code sqlFunctions}. Any errors will be handled by the given
     * {@code errorHandler}.
     *
     * @param connection   connection to the underlying database that should be used for the update
     * @param errorHandler handles errors as result of executing the update
     * @param sqlFunctions the functions that produce the update statements
     * @return an array of update counts containing one element for each sql function
     */
    public static int[] executeUpdates(Connection connection,
                                       Consumer<SQLException> errorHandler,
                                       SqlFunction... sqlFunctions) {
        try {
            int[] result = new int[sqlFunctions.length];
            IntStream.range(0, sqlFunctions.length).forEach(i -> {
                SqlFunction sqlFunction = sqlFunctions[i];
                PreparedStatement preparedStatement = createSqlStatement(connection, sqlFunction);
                try {
                    result[i] = preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    errorHandler.accept(e);
                } finally {
                    closeQuietly(preparedStatement);
                }
            });
            return result;
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Execute the a batch update or insert statement produced by the given {@code sqlFunction}. Any errors will be
     * handled by the given {@code errorHandler}.
     *
     * @param connection   connection to the underlying database that should be used for the update
     * @param sqlFunction  the function that produces the batch update statement
     * @param errorHandler handles errors as result of executing the update
     * @return an array of update counts containing one element for each sql function
     */
    public static int[] executeBatch(Connection connection, SqlFunction sqlFunction,
                                     Consumer<SQLException> errorHandler) {
        try {
            PreparedStatement preparedStatement = createSqlStatement(connection, sqlFunction);
            try {
                return preparedStatement.executeBatch();
            } catch (SQLException e) {
                errorHandler.accept(e);
            } finally {
                closeQuietly(preparedStatement);
            }
        } finally {
            closeQuietly(connection);
        }
        return new int[0];
    }

    /**
     * Create a converter that produces a List of results of type {@code R} from a converter that produces a single
     * result. The returned converter iterates over the resultSet until all results have been converted and added to
     * the list.
     *
     * @param singleResultConverter the converter that can convert a single result from the current position of the
     *                              resultSet
     * @param <R>                   the type of result produced by the {@code singleResultConverter}
     * @return converter that produces a list of results
     */
    public static <R> SqlResultConverter<List<R>> listResults(SqlResultConverter<R> singleResultConverter) {
        return resultSet -> {
            List<R> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(singleResultConverter.apply(resultSet));
            }
            return results;
        };
    }

    /**
     * Close the given {@code resultSet}, if possible. All exceptions are discarded.
     *
     * @param resultSet The resource to close. May be {@code null}.
     */
    public static void closeQuietly(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException ignore) {
            }
        }
    }

    /**
     * Close the given {@code statement}, if possible. All exceptions are discarded.
     *
     * @param statement The resource to close. May be {@code null}.
     */
    public static void closeQuietly(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ignore) {
            }
        }
    }

    /**
     * Close the given {@code connection}, if possible. All exceptions are discarded.
     *
     * @param connection The resource to close. May be {@code null}.
     */
    public static void closeQuietly(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            //ignore
        }
    }

    private static PreparedStatement createSqlStatement(Connection connection, SqlFunction sqlFunction) {
        try {
            return sqlFunction.apply(connection);
        } catch (SQLException e) {
            throw new JdbcException("Failed to create a SQL statement", e);
        }
    }

    /**
     * Moves the {@code resultSet} cursor forward and then read the object at the given column (base 1). Please note
     * that this method changes the {@code resultSet} cursor position. If the {@code resultSet} reaches the end, this
     * method returns {@code null}. This method reads the object at the given column (base 1), which object can be
     * {@code null} as well.
     * <p>
     * Please use the method {@link #extract(ResultSet, int, Class)} if you do not need to move the {@code resultSet}
     * cursor.
     * <p>
     * This method makes use of the {@link ResultSet#wasNull()} method to verify whether the object that was read was
     * {@code null} or not. There are cases where the database driver (such as MySQL) returns the default primitive
     * value instead of {@code null}. This method avoids this problem.
     *
     * @param resultSet  The ResultSet to extract data from.
     * @param column     The index of the column containing to read.
     * @param columnType The expected type of data in the column.
     * @return The next value in the specified column or {@code null} if no data was present.
     * @throws SQLException         If an error occurs while reading the object.
     * @throws NullPointerException If the {@code resultSet} or {@code columnType} are {@code null}.
     * @see #extract(ResultSet, int, Class, Object)
     */
    public static <T> T nextAndExtract(ResultSet resultSet,
                                       int column,
                                       Class<T> columnType) throws SQLException, NullPointerException {
        return nextAndExtract(resultSet, column, columnType, null);
    }

    /**
     * Moves the {@code resultSet} cursor forward and then read the object at the given column (base 1). Please note
     * that this method changes the {@code resultSet} cursor position. If the {@code resultSet} reaches the end, this
     * method returns the given {@code defaultValue}. This method reads the object at the given column (base 1), which
     * may be defaulted to the {@code defaultValue} as well.
     * <p>
     * Please use the method {@link #extract(ResultSet, int, Class, Object)} if you do not need to move the
     * {@code resultSet} cursor.
     * <p>
     * This method makes use of the {@link ResultSet#wasNull()} method to verify whether the object that was read was
     * {@code null} or not. There are cases where the database driver (such as MySQL) returns the default primitive
     * value instead of {@code null}. This method avoids this problem.
     *
     * @param resultSet    The ResultSet to extract data from.
     * @param column       The index of the column containing to read.
     * @param columnType   The expected type of data in the column.
     * @param defaultValue The default value to return if no data was present the {@code resultSet}.
     * @return The next value in the specified column or the {@code defaultValue} if no data was present.
     * @throws SQLException         If an error occurs while reading the object.
     * @throws NullPointerException If the {@code resultSet} or {@code columnType} are {@code null}.
     * @see #extract(ResultSet, int, Class, Object)
     */
    public static <T> T nextAndExtract(ResultSet resultSet,
                                       int column,
                                       Class<T> columnType,
                                       T defaultValue) throws SQLException, NullPointerException {
        return resultSet.next() ? extract(resultSet, column, columnType, defaultValue) : defaultValue;
    }

    /**
     * Returns the object read from the {@code resultSet}, which object can be {@code null}, at the given column (base
     * 1).
     * <p>
     * This method makes use of the {@link ResultSet#wasNull()} method to verify whether the object that was read was
     * {@code null} or not. There are cases where the database driver (such as MySQL) returns the default primitive
     * value instead of {@code null}. This method avoids this problem.
     *
     * @param resultSet  The result set from where the object is read (which cannot be {@code null}).
     * @param column     The column index (which starts from 1).
     * @param columnType The object type (which cannot be {@code null}).
     * @return The object read from the {@code resultSet}, which object can be {@code null}, at the given column (base
     * 1).
     * @throws SQLException         If an error occurs while reading the object.
     * @throws NullPointerException If the {@code resultSet} or {@code columnType} are {@code null}.
     */
    public static <T> T extract(ResultSet resultSet,
                                int column,
                                Class<T> columnType) throws SQLException, NullPointerException {
        return extract(resultSet, column, columnType, null);
    }

    /**
     * Returns the object read from the {@code resultSet}, which object can be {@code null}, at the given column (base
     * 1).
     * <p>
     * This method makes use of the {@link ResultSet#wasNull()} method to verify whether the object that was read was
     * {@code null} or not. There are cases where the database driver (such as MySQL) returns the default primitive
     * value instead of {@code null}. This method avoids this problem.
     *
     * @param resultSet  the result set from where the object is read (which cannot be {@code null})
     * @param column     the column index (which starts from 1)
     * @param columnType the object type (which cannot be {@code null})
     * @return the object read from the {@code resultSet}, which object can be {@code null}, at the given column (base
     * 1).
     * @throws SQLException         if an error occurs while reading the object
     * @throws NullPointerException if the {@code resultSet} or {@code columnType} are {@code null}
     */
    public static <T> T extract(ResultSet resultSet,
                                int column,
                                Class<T> columnType,
                                T defaultValue) throws SQLException, NullPointerException {
        final T value = resultSet.getObject(column, columnType);
        return value == null || resultSet.wasNull() ? defaultValue : value;
    }

    /**
     * Private default constructor
     */
    private JdbcUtils() {
    }

    /**
     * Describes a function that creates a new {@link PreparedStatement} ready to be executed.
     */
    @FunctionalInterface
    public interface SqlFunction {
        /**
         * Create a new {@link PreparedStatement} using the given {@code connection}.
         *
         * @param connection the connection that will be used to create the statement
         * @return a new statement ready for execution
         * @throws SQLException if the statement could not be created
         */
        PreparedStatement apply(Connection connection) throws SQLException;
    }

    /**
     * Describes a function that converts a {@link ResultSet} into a result of type {@code R}.
     */
    @FunctionalInterface
    public interface SqlResultConverter<R> {
        /**
         * Convert the given resultSet to a result of type {@code R}.
         *
         * @param resultSet the sql result set containing results of a prior sql query
         * @return the conversion result
         * @throws SQLException if the results could not be converted
         */
        R apply(ResultSet resultSet) throws SQLException;
    }
}
