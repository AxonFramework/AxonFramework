/*
 * Copyright (c) 2010-2023. Axon Framework
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
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class with some specific hacks required to get certain features to work with Oracle v11.
 */
public class Oracle11Utils {

    private Oracle11Utils() {
    }

    /**
     * Oracle11 does not have auto incremented values. This method uses a sequence and a trigger to create the same
     * behavior.
     *
     * @param connection    The connection to the database that will be used to execute the queries
     * @param tableName     The name of the table that contains the column that should be automatically incremented
     * @param columnName    The name of the column that should be automatically incremented
     * @throws SQLException if the auto increment statement cannot be created or executed
     */
    public static void simulateAutoIncrement(Connection connection, String tableName, String columnName) throws SQLException {
        String sequenceName = tableName + "_seq";
        String triggerName = tableName + "_id";

        try (PreparedStatement pst = connection.prepareStatement(
                "CREATE sequence " + sequenceName + " start with 1 increment by 1 nocycle")) {
            pst.executeUpdate();
        }
        // The oracle driver is getting confused by this statement, claiming we have to declare some in out parameter,
        // when we execute this as a prepared statement. Might be a config issue on our side.
        try (Statement st = connection.createStatement()) {
            st.execute("create or replace trigger " + triggerName +
                            "        before insert on " + tableName +
                            "        for each row " +
                            "        begin " +
                            "                :new." + columnName + " := " + sequenceName + ".nextval; " +
                            "        end;"
            );
        }
    }

    /**
     * Creates a prepared statement that acts as a null object.
     *
     * @param connection The connection that is used to create the prepared statement
     * @return PreparedStatement
     * @throws SQLException if the null statement cannot be created
     */
    public static PreparedStatement createNullStatement(Connection connection) throws SQLException {
        return connection.prepareStatement("select 1 from dual");
    }
}
