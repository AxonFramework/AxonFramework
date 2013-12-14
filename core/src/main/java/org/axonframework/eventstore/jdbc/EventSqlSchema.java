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

package org.axonframework.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * @author Kristian Rosenvold
 */
public interface EventSqlSchema {
    PreparedStatement sql_loadLastSnapshot(Connection connection, Object identifier, String aggregateType);

    PreparedStatement sql_doInsert(String tableName, Connection connection, Object... params);

    PreparedStatement sql_pruneSnapshots(Connection connection, String type, Object aggregateIdentifier, Long sequenceOfFirstSnapshotToPrune);

    PreparedStatement sql_findRedundantSnapshots(Connection connection, String type, Object aggregateIdentifier);

    PreparedStatement sql_fetchFromSequenceNumber(Connection connection, Object identifier, String type, long firstSequenceNumber);

    PreparedStatement sql_getFetchAll(String whereClause, Connection nonAutoCommittConnection, Object[] objects);

    PreparedStatement sql_delete_all_snapshotEvenEntries(Connection connection);

    PreparedStatement sql_deleteAllDomainEventEntries(Connection connection);

    PreparedStatement sql_createSnapshotEventEntry(Connection connection);

    PreparedStatement sql_createDomainEventEntry(Connection connection);
}
