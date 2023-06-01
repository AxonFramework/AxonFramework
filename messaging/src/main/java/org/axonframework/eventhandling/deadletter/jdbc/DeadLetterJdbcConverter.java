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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.DeadLetter;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Steven van Beelen
 * @since 4.8.0
 */
@FunctionalInterface
public interface DeadLetterJdbcConverter<M extends EventMessage<?>> {

    DeadLetter<? extends M> convertToLetter(ResultSet resultSet) throws SQLException;
}
