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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class for silently closing JDBC resources
 *
 * @author Kristian Rosenvold
 * @author Allard Buijze
 * @since 2.2
 */
public class JdbcUtils {

    /**
     * Close the given <code>resultSet</code>, if possible. All exceptions are discarded.
     *
     * @param resultSet The resource to close. May be <code>null</code>.
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
     * Close the given <code>statement</code>, if possible. All exceptions are discarded.
     *
     * @param statement The resource to close. May be <code>null</code>.
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
     * Close the given <code>connection</code>, if possible. All exceptions are discarded.
     *
     * @param connection The resource to close. May be <code>null</code>.
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
}
