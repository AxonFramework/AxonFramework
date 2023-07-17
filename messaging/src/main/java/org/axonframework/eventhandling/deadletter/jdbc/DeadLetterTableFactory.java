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
 * A functional interface to create a JDBC-specific {@link org.axonframework.messaging.deadletter.DeadLetter} entry
 * table and its indices.
 *
 * @author Steven van Beelen
 * @since 4.8.0
 */
@FunctionalInterface
public interface DeadLetterTableFactory {

    /**
     * Creates a {@link Statement} to use for construction of a
     * {@link org.axonframework.messaging.deadletter.DeadLetter} entry table and its indices.
     * <p>
     * The returned {@code Statement} typically contains several SQL statements and hence the invoker is inclined to
     * execute the {@code Statement} as a batch by invoking {@link Statement#executeBatch()}. Furthermore, it is
     * expected that this statement at least constructs the required uniqueness constraints.
     *
     * @param connection The connection to create the {@link Statement} with.
     * @param schema     The schema defining the table and column names.
     * @return A {@link Statement statement} to create the table and its indices with, ready to be
     * {@link Statement#executeBatch() executed}.
     * @throws SQLException when an exception occurs while creating the {@link Statement}.
     */
    Statement createTableStatement(Connection connection, DeadLetterSchema schema) throws SQLException;
}
