/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Rene de Waele
 */
public interface EventSchemaFactory {

    /**
     * Creates a PreparedStatement that allows for the creation of the table to store Event entries.
     *
     * @param connection The connection to create the PreparedStatement for
     * @return The Prepared Statement, ready to be executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement createDomainEventTable(Connection connection,
                                             EventSchemaConfiguration schemaConfiguration) throws SQLException;

    /**
     * Creates a PreparedStatement that allows for the creation of the table to store Snapshots.
     *
     * @param connection The connection to create the PreparedStatement for
     * @return The Prepared Statement, ready to be executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement createSnapshotEventTable(Connection connection,
                                               EventSchemaConfiguration schemaConfiguration) throws SQLException;

}
