/*
 * Copyright (c) 2010-2012. Axon Framework
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
package org.axonframework.saga.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.axonframework.common.io.JdbcUtils.createPreparedStatement;

/**
 * @author Kristian Rosenvold
 */
public class GenricSagaSqlSchema implements SagaSqlSchema {
    @Override
    public PreparedStatement sql_loadSaga(Connection connection, String sagaId) {
        final String sql = "SELECT se.serializedSaga, se.sagaType, se.revision FROM SagaEntry se WHERE se.sagaId = ?";
        return createPreparedStatement(sql, connection, sagaId);
    }

    @Override
    public PreparedStatement sql_removeAssocValue(Connection connection, String key, String value, String sagaType, String sagaIdentifier) {
        final String sql = "DELETE FROM AssociationValueEntry ae "
                + "WHERE ae.associationKey = ? "
                + "AND ae.associationValue = ? "
                + "AND ae.sagaType = ? "
                + "AND ae.sagaId = ?";
        return createPreparedStatement(sql, connection, key, value, sagaType, sagaIdentifier);
    }

    @Override
    public PreparedStatement sql_storeAssocValue(Connection connection, String key, String value, String sagaType, String sagaIdentifier) {
        final String sql = "insert into AssociationValueEntry(associationKey,associationValue,sagaType,sagaId) values(?,?,?,?)";
        return createPreparedStatement(sql, connection, key, value, sagaType, sagaIdentifier);
    }

    @Override
    public PreparedStatement sql_findAssocSagaIdentifiers(Connection connection, String key, String value, String sagaType) {
        final String sql = "SELECT ae.sagaId FROM AssociationValueEntry ae "
                + "WHERE ae.associationKey = ? "
                + "AND ae.associationValue = ? "
                + "AND ae.sagaType = ?";
        return createPreparedStatement(sql, connection, key, value, sagaType);
    }

    @Override
    public PreparedStatement sql_deleteSagaEntry(Connection connection, String sagaIdentifier) {
        final String sql = "DELETE FROM SagaEntry se WHERE se.sagaId = ?";
        return createPreparedStatement(sql, connection, sagaIdentifier);
    }

    @Override
    public PreparedStatement sql_deleteAssocValueEntry(Connection connection, String sagaIdentifier) {
        final String sql = "DELETE FROM AssociationValueEntry ae WHERE ae.sagaId = ?";
        return createPreparedStatement(sql, connection, sagaIdentifier);
    }

    @Override
    public PreparedStatement sql_updateSaga(Connection connection, byte[] serializedSaga, String revision, String sagaId, String sagaType) {
        final String sql = "UPDATE SagaEntry s SET s.serializedSaga = ?, s.revision = ? WHERE s.sagaId = ? AND s.sagaType = ?";
        return createPreparedStatement(sql, connection, serializedSaga, revision, sagaId, sagaType);
    }

    @Override
    public PreparedStatement sql_storeSaga(Connection connection, String sagaId, String revision, String sagaType, byte[] serializedSaga) {
        final String sql = "insert into SagaEntry(sagaId, revision, sagaType, serializedSaga) values(?,?,?,?)";
        return createPreparedStatement(sql, connection, sagaId, revision, sagaType, serializedSaga);
    }

    @Override
    public PreparedStatement sql_deleteAllSagaEntries(Connection conn) {
        return createPreparedStatement("DELETE FROM SagaEntry", conn);
    }

    @Override
    public PreparedStatement sql_deleteAllAssocValueEntries(Connection conn) {
        return createPreparedStatement("DELETE FROM AssociationValueEntry", conn);
    }

    @Override
    public PreparedStatement sql_createTableAssocValueEntry(Connection conn) {
        final String sql = "create table AssociationValueEntry (\n" +
                "        id int not null AUTO_GENERATED,\n" +
                "        associationKey varchar(255),\n" +
                "        associationValue varchar(255),\n" +
                "        sagaId varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        primary key (id)\n" +
                "    );\n";
        return createPreparedStatement(sql, conn);
    }

    @Override
    public PreparedStatement sql_createTableSagaEntry(Connection conn) {
        final String sql = "    create table SagaEntry (\n" +
                "        sagaId varchar(255) not null,\n" +
                "        revision varchar(255),\n" +
                "        sagaType varchar(255),\n" +
                "        serializedSaga blob,\n" +
                "        primary key (sagaId)\n" +
                "    );";
        return createPreparedStatement(sql, conn);
    }
}
