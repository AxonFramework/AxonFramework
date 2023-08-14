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

import org.axonframework.eventhandling.EventMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A functional interface describing how to convert a {@link ResultSet} in {@link JdbcDeadLetter} implementation of type
 * {@code D}
 *
 * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation this
 *            converter converts.
 * @param <D> An implementation of {@link JdbcDeadLetter} converted by this converter.
 * @author Steven van Beelen
 * @since 4.8.0
 */
@FunctionalInterface
public interface DeadLetterJdbcConverter<E extends EventMessage<?>, D extends JdbcDeadLetter<E>> {

    /**
     * Converts the given {@code resultSet} in an implementation of {@link JdbcDeadLetter}.
     * <p>
     * It is recommended to validate the type of {@link EventMessage} to place in the result, as different types require
     * additional information to be deserialized and returned.
     *
     * @param resultSet The {@link ResultSet} to convert into a {@link JdbcDeadLetter}
     * @return An implementation of {@link JdbcDeadLetter} based on the given {@code resultSet}.
     * @throws SQLException if the a {@code columnLabel} in the given {@code resultSet} does not exist, if a database
     *                      access error occurs or if the given {@code resultSet} is closed.
     */
    D convertToLetter(ResultSet resultSet) throws SQLException;
}
