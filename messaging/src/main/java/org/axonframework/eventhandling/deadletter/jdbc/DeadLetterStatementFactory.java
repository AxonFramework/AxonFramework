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
import java.sql.SQLException;
import javax.annotation.Nonnull;

/**
 * @param <E> An implementation of {@link EventMessage} todo...
 * @author Steven van Beelen
 * @since 4.8.0
 */
public interface DeadLetterStatementFactory<E extends EventMessage<?>> {

    PreparedStatement enqueueStatement(@Nonnull Connection connection,
                                       @Nonnull String processingGroup,
                                       @Nonnull String sequenceIdentifier,
                                       @Nonnull DeadLetter<? extends E> letter,
                                       long sequenceIndex) throws SQLException;

    PreparedStatement evictStatement(@Nonnull Connection connection,
                                     @Nonnull String letterIdentifier) throws SQLException;

    PreparedStatement containsStatement(@Nonnull Connection connection,
                                        @Nonnull String processingGroup,
                                        @Nonnull String sequenceId) throws SQLException;

    PreparedStatement letterSequenceStatement(@Nonnull Connection connection,
                                              @Nonnull String processingGroup,
                                              @Nonnull String sequenceId,
                                              int firstResult,
                                              int maxSize) throws SQLException;

    PreparedStatement sequenceIdentifiersStatement(@Nonnull Connection connection,
                                                   @Nonnull String processingGroup) throws SQLException;

    PreparedStatement sizeStatement(@Nonnull Connection connection,
                                    @Nonnull String processingGroup) throws SQLException;

    PreparedStatement sequenceSizeStatement(@Nonnull Connection connection,
                                            @Nonnull String processingGroup,
                                            @Nonnull String sequenceId) throws SQLException;

    PreparedStatement amountOfSequencesStatement(@Nonnull Connection connection,
                                                 @Nonnull String processingGroup) throws SQLException;

    PreparedStatement clearStatement(@Nonnull Connection connection,
                                     @Nonnull String processingGroup) throws SQLException;

    PreparedStatement maxIndexStatement(@Nonnull Connection connection,
                                        @Nonnull String processingGroup,
                                        @Nonnull String sequenceId) throws SQLException;


}
