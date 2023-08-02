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

package org.axonframework.eventhandling.tokenstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Jdbc token entry table factory for Oracle databases.
 *
 * @author Rene de Waele
 */
public class Oracle11TokenTableFactory implements TokenTableFactory {

    /**
     * Creates a singleton reference the the Oracle11TokenTableFactory implementation.
     */
    public static final Oracle11TokenTableFactory INSTANCE = new Oracle11TokenTableFactory();

    protected Oracle11TokenTableFactory() {
    }

    @Override
    public PreparedStatement createTable(Connection connection, TokenSchema schema) throws SQLException {
        String sql = "CREATE TABLE " + schema.tokenTable() + " (\n" +
                schema.processorNameColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.segmentColumn() + " INTEGER NOT NULL,\n" +
                schema.tokenColumn() + " " + " BLOB NULL,\n" +
                schema.tokenTypeColumn() + " VARCHAR(255) NULL,\n" +
                schema.timestampColumn() + " VARCHAR(255) NULL,\n" +
                schema.ownerColumn() + " VARCHAR(255) NULL,\n" +
                "PRIMARY KEY (" + schema.processorNameColumn() + "," + schema.segmentColumn() + ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }
}
