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

package org.axonframework.eventhandling.tokenstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Interface describing a factory for JDBC to create the table containing tracking token entries.
 *
 * @author Rene de Waele
 */
public interface TokenTableFactory {

    /**
     * Creates a PreparedStatement that allows for the creation of the table to store tracking token entries.
     *
     * @param connection The connection to create the PreparedStatement for
     * @param schema     The token schema with the name of the table and its columns
     * @return The statement to create the table, ready to be executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement createTable(Connection connection, TokenSchema schema) throws SQLException;
}
