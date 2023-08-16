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
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A {@link DeadLetterTableFactory} implementation compatible with most databases.
 *
 * @author Steven van Beelen
 * @since 4.8.0
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class GenericDeadLetterTableFactory implements DeadLetterTableFactory {

    @SuppressWarnings("SqlNoDataSourceInspection")
    @Override
    public Statement createTableStatement(Connection connection, DeadLetterSchema schema) throws SQLException {
        Statement statement = connection.createStatement();
        statement.addBatch(createTableSql(schema));
        statement.addBatch(processingGroupIndexSql(schema));
        statement.addBatch(sequenceIdentifierIndexSql(schema));
        return statement;
    }

    /**
     * Constructs the SQL to create a dead-letter table, using the given {@code schema} to deduce the table and column
     * names.
     *
     * @param schema The schema defining the table and column names.
     * @return The SQL to construct the dead-letter table.
     */
    protected String createTableSql(DeadLetterSchema schema) {
        return "CREATE TABLE IF NOT EXISTS " + schema.deadLetterTable() + " (\n" +
                schema.deadLetterIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.processingGroupColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.sequenceIndexColumn() + " BIGINT NOT NULL,\n" +
                schema.messageTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.eventIdentifierColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.timestampColumn() + " " + timestampType() + " NOT NULL,\n" +
                schema.payloadTypeColumn() + " VARCHAR(255) NOT NULL,\n" +
                schema.payloadRevisionColumn() + " VARCHAR(255),\n" +
                schema.payloadColumn() + " " + serializedDataType() + " NOT NULL,\n" +
                schema.metaDataColumn() + " " + serializedDataType() + ",\n" +
                schema.aggregateTypeColumn() + " VARCHAR(255),\n" +
                schema.aggregateIdentifierColumn() + " VARCHAR(255),\n" +
                schema.sequenceNumberColumn() + " BIGINT,\n" +
                schema.tokenTypeColumn() + " VARCHAR(255),\n" +
                schema.tokenColumn() + " " + serializedDataType() + ",\n" +
                schema.enqueuedAtColumn() + " " + timestampType() + " NOT NULL,\n" +
                schema.lastTouchedColumn() + " " + timestampType() + ",\n" +
                schema.processingStartedColumn() + " " + timestampType() + ",\n" +
                schema.causeTypeColumn() + " VARCHAR(255),\n" +
                schema.causeMessageColumn() + " VARCHAR(1023),\n" +
                schema.diagnosticsColumn() + " " + serializedDataType() + ",\n" +
                "CONSTRAINT PK PRIMARY KEY (" + schema.deadLetterIdentifierColumn() + "),\n" +
                "CONSTRAINT " + schema.sequenceIndexColumn() + "_INDEX UNIQUE (" +
                schema.processingGroupColumn() + "," +
                schema.sequenceIdentifierColumn() + "," +
                schema.sequenceIndexColumn() +
                ")\n)";
    }

    /**
     * Constructs the SQL to create an index of the {@link DeadLetterSchema#processingGroupColumn() processing group} ,
     * using the given {@code schema} to deduce the table and column names.
     *
     * @param schema The schema defining the table and column names.
     * @return The SQL to construct the index for the {@link DeadLetterSchema#processingGroupColumn() processing group}
     * for the dead-letter table.
     */
    protected String processingGroupIndexSql(DeadLetterSchema schema) {
        return "CREATE INDEX " + schema.processingGroupColumn() + "_INDEX "
                + "ON " + schema.deadLetterTable() + " "
                + "(" + schema.processingGroupColumn() + ")";
    }

    /**
     * Constructs the SQL to create an index for the {@link DeadLetterSchema#processingGroupColumn() processing group}
     * and {@link DeadLetterSchema#sequenceIdentifierColumn() sequence indentifier} combination, using the given
     * {@code schema} to deduce the table and column names.
     *
     * @param schema The schema defining the table and column names.
     * @return The SQL to construct the index for {@link DeadLetterSchema#processingGroupColumn() processing group} and
     * {@link DeadLetterSchema#sequenceIdentifierColumn() combination for the dead-letter table.
     */
    protected String sequenceIdentifierIndexSql(DeadLetterSchema schema) {
        return "CREATE INDEX " + schema.sequenceIdentifierColumn() + "_INDEX "
                + "ON " + schema.deadLetterTable() + " "
                + "(" + schema.processingGroupColumn() + "," + schema.sequenceIdentifierColumn() + ")";
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
     * {@link DeadLetterSchema#processingGroupColumn()}, and the {@link DeadLetterSchema#timestampColumn()}. Defaults to
     * {@code VARCHAR(255)}.
     *
     * @return The SQL to describe the type for timestamp columns.
     */
    protected String timestampType() {
        return "VARCHAR(255)";
    }
}
