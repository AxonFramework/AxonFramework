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
package org.axonframework.eventhandling.saga.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * SQL schema supporting postgres databases.
 * <p/>
 * This implementation uses the appropriate postgres data types (serial, bytea).
 *
 * @author Jochen Munz
 * @since 2.4
 */
public class PostgresSagaSqlSchema extends GenericSagaSqlSchema {

    public PostgresSagaSqlSchema() {
    }

    public PostgresSagaSqlSchema(SchemaConfiguration schemaConfiguration) {
        super(schemaConfiguration);
    }

    @Override
    public PreparedStatement sql_createTableAssocValueEntry(Connection conn) throws SQLException {
        final String sql = "create table " + schemaConfiguration.assocValueEntryTable() + " (\n" +
                "        id bigserial not null,\n" +
                "        associationKey varchar(255),\n" +
                "        associationValue varchar(255),\n" +
                "        sagaId varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        primary key (id)\n" +
                "    );\n";
        return conn.prepareStatement(sql);
    }

    @Override
    public PreparedStatement sql_createTableSagaEntry(Connection conn) throws SQLException {
        return conn.prepareStatement("create table " + schemaConfiguration.sagaEntryTable() + " (\n" +
                "        sagaId varchar(255) not null,\n" +
                "        revision varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        serializedSaga bytea,\n" +
                "        primary key (sagaId)\n" +
                "    );");
    }


}
