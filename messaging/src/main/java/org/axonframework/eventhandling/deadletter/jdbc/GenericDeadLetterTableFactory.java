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

package org.axonframework.eventhandling.deadletter.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A {@link DeadLetterTableFactory} implementation compatible with most databases.
 *
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class GenericDeadLetterTableFactory implements DeadLetterTableFactory {

    @SuppressWarnings("SqlNoDataSourceInspection")
    @Override
    public PreparedStatement create(Connection connection, DeadLetterSchema schema) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + schema.deadLetterTable() + " (\n" +
                schema.deadLetterIdColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.processingGroupColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceIndexColumn() + " VARCHAR(255) NOT NULL,\n" +
                // TODO validate whether the below is the best way forward
                schema.messageTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timeStampColumn() + " " + timestampType() + " NOT NULL,\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadColumn() + " " + serializedDataType() + " NOT NULL,\n" +
                schema.metaDataColumn() + " " + serializedDataType() + " ,\n" +
                schema.aggregateTypeColumn() + " VARCHAR(255),\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255),\n" +
                schema.sequenceNumberColumn() + " BIGINT NOT NULL,\n" +
                schema.tokenTypeColumn() + " VARCHAR(255),\n" +
                schema.tokenColumn() + " " + serializedDataType() + " ,\n" +
                // TODO validate whether the above is the best way forward
                schema.enqueuedAtColumn() + " " + timestampType() + " NOT NULL,\n" +
                schema.lastTouchedColumn() + " " + timestampType() + " ,\n" +
                schema.processingStartedColumn() + " " + timestampType() + " ,\n" +
                schema.causeTypeColumn() + " VARCHAR(255),\n" +
                schema.causeMessageColumn() + " VARCHAR(255),\n" + // TODO large size for the message?
                schema.diagnosticsColumn() + " " + serializedDataType() + " ,\n" +
                "PRIMARY KEY (" + schema.deadLetterIdColumn() + "),\n" +
                "UNIQUE (" + schema.processingGroupColumn() + "),\n" +
                "UNIQUE (" + schema.processingGroupColumn() + "," + schema.sequenceIdentifierColumn() + "),\n" +
                "UNIQUE (" +
                schema.processingGroupColumn() + "," + schema.sequenceIdentifierColumn() + "," +
                schema.sequenceIndexColumn() +
                ")\n" +
                ")";
        return connection.prepareStatement(sql);
    }

    /**
     * Returns the SQL to describe the type for serialized data columns.
     * <p>
     * Used for the {@link DeadLetterSchema#payloadColumn()}, {@link DeadLetterSchema#metaDataColumn()},
     * {@link DeadLetterSchema#tokenColumn()}, and the {@link DeadLetterSchema#diagnosticsColumn()}. Defaults to
     * {@code BLOB}.
     *
     * @return The SQL to describe the type for serialized data columns.
     */
    protected String serializedDataType() {
        return "BLOB";
    }

    /**
     * Returns the SQL to describe the type for timestamp columns.
     * <p>
     * Used for the {@link DeadLetterSchema#enqueuedAtColumn()}, {@link DeadLetterSchema#lastTouchedColumn()},
     * {@link DeadLetterSchema#processingGroupColumn()}, and the {@link DeadLetterSchema#timeStampColumn()}. Defaults to
     * {@code VARCHAR(255)}.
     *
     * @return The SQL to describe the type for timestamp columns.
     */
    protected String timestampType() {
        return "VARCHAR(255)";
    }
}
