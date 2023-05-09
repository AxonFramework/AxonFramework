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
 * A functional interface to create a JDBC-specific {@link org.axonframework.messaging.deadletter.DeadLetter} entry
 * table.
 *
 * @author Steven van Beelen
 * @since 4.8.0
 */
@FunctionalInterface
public interface DeadLetterTableFactory {

    /**
     * Creates a {@link PreparedStatement} to use for construction of a
     * {@link org.axonframework.messaging.deadletter.DeadLetter} entry table.
     *
     * @param connection The connection to create the {@link PreparedStatement}.
     * @param schema     The schema defining the table and column names.
     * @return A {@link PreparedStatement statement} to create the table, ready to be executed.
     * @throws SQLException when an exception occurs while creating the {@link PreparedStatement}.
     */
    PreparedStatement create(Connection connection, DeadLetterSchema schema) throws SQLException;
}
