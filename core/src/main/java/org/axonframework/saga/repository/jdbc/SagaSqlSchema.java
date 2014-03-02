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
package org.axonframework.saga.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * @author Kristian Rosenvold
 */
public interface SagaSqlSchema {
    PreparedStatement sql_loadSaga(Connection connection, String sagaId);

    PreparedStatement sql_removeAssocValue(Connection connection, String key, String value, String sagaType, String sagaIdentifier);

    PreparedStatement sql_storeAssocValue(Connection connection, String key, String value, String sagaType, String sagaIdentifier);

    PreparedStatement sql_findAssocSagaIdentifiers(Connection connection, String key, String value, String sagaType);

    PreparedStatement sql_deleteSagaEntry(Connection connection, String sagaIdentifier);

    PreparedStatement sql_deleteAssocValueEntry(Connection connection, String sagaIdentifier);

    PreparedStatement sql_updateSaga(Connection connection, byte[] serializedSaga, String revision, String sagaId, String sagaType);

    PreparedStatement sql_storeSaga(Connection connection, String sagaId, String revision, String sagaType, byte[] serializedSaga);

    PreparedStatement sql_deleteAllSagaEntries(Connection conn);

    PreparedStatement sql_deleteAllAssocValueEntries(Connection conn);

    PreparedStatement sql_createTableAssocValueEntry(Connection conn);

    PreparedStatement sql_createTableSagaEntry(Connection conn);
}
