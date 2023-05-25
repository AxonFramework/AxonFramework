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
import org.axonframework.messaging.deadletter.DeadLetter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.annotation.Nonnull;

/**
 * @author Steven van Beelen
 * @since 4.8.0
 */
public interface JdbcDeadLetterConverter<M extends EventMessage<?>> {

    PreparedStatement enqueueStatement(@Nonnull String sequenceIdentifier,
                                       @Nonnull DeadLetter<? extends M> letter,
                                       long sequenceIndex,
                                       @Nonnull Connection connection) throws SQLException;

    DeadLetter<? extends M> convertToLetter(ResultSet resultSet) throws SQLException;
}
