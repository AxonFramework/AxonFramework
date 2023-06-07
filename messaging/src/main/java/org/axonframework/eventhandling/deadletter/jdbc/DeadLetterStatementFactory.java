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
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

/**
 * A contract towards <b>all</b> {@link PreparedStatement PreparedStatements} a {@link JdbcSequencedDeadLetterQueue}
 * requires to function.
 *
 * @param <E> An implementation of {@link EventMessage} within the {@link DeadLetter} this factory constructs
 *            {@link PreparedStatement PreparedStatements} for.
 * @author Steven van Beelen
 * @since 4.8.0
 */
public interface DeadLetterStatementFactory<E extends EventMessage<?>> {

    /**
     * Constructs the {@link PreparedStatement} used for the
     * {@link JdbcSequencedDeadLetterQueue#enqueue(Object, DeadLetter)} operation.
     *
     * @param connection         The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup    The processing group for which to enqueue the given {@code letter}.
     * @param sequenceIdentifier The identifier of the sequence the letter belongs to.
     * @param letter             The letter to enqueue.
     * @param sequenceIndex      The index of the letter within the sequence, to ensure the processing order is
     *                           maintained.
     * @return The {@link PreparedStatement} used to
     * {@link JdbcSequencedDeadLetterQueue#enqueue(Object, DeadLetter) enqueue}.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement enqueueStatement(@Nonnull Connection connection,
                                       @Nonnull String processingGroup,
                                       @Nonnull String sequenceIdentifier,
                                       @Nonnull DeadLetter<? extends E> letter,
                                       long sequenceIndex) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used to retrieve the maximum
     * {@link DeadLetterSchema#sequenceIndexColumn() index} of the sequence identified with the given
     * {@code sequenceIdentifier}.
     * <p>
     * Used by the {@link JdbcSequencedDeadLetterQueue#enqueue(Object, DeadLetter)} to deduce the index of the
     * {@link DeadLetter} in its sequence.
     *
     * @param connection         The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup    The processing group for which to retrieve the maximum
     *                           {@link DeadLetterSchema#sequenceIndexColumn() index} of the sequence identified through
     *                           the given {@code sequenceIdentifier}.
     * @param sequenceIdentifier The identifier of the sequence for which to retrieve the maximum
     *                           {@link DeadLetterSchema#sequenceIndexColumn() index} for.
     * @return The {@link PreparedStatement} used to retrieve the maximum
     * {@link DeadLetterSchema#sequenceIndexColumn() index} with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement maxIndexStatement(@Nonnull Connection connection,
                                        @Nonnull String processingGroup,
                                        @Nonnull String sequenceIdentifier) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the {@link JdbcSequencedDeadLetterQueue#evict(DeadLetter)}
     * operation.
     *
     * @param connection The {@link Connection} used to create the {@link PreparedStatement}.
     * @param identifier The identifier of the {@link DeadLetter} to evict.
     * @return The {@link PreparedStatement} used to {@link JdbcSequencedDeadLetterQueue#evict(DeadLetter) evict}.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement evictStatement(@Nonnull Connection connection,
                                     @Nonnull String identifier) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the
     * {@link JdbcSequencedDeadLetterQueue#requeue(DeadLetter, UnaryOperator)} operation.
     *
     * @param connection  The {@link Connection} used to create the {@link PreparedStatement}.
     * @param identifier  The identifier of the {@link DeadLetter} to requeue.
     * @param cause       The cause of requeueing the {@link DeadLetter} identified through the given
     *                    {@code identifier}.
     * @param lastTouched The {@link Instant} the {@link DeadLetter} to requeue was last processed.
     * @param diagnostics The new diagnostics to attach to the {@link DeadLetter} to requeue.
     * @return The {@link PreparedStatement} used to
     * {@link JdbcSequencedDeadLetterQueue#requeue(DeadLetter, UnaryOperator) requeue}.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement requeueStatement(@Nonnull Connection connection,
                                       @Nonnull String identifier,
                                       Cause cause,
                                       @Nonnull Instant lastTouched,
                                       MetaData diagnostics) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the {@link JdbcSequencedDeadLetterQueue#contains(Object)}
     * operation.
     *
     * @param connection         The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup    The processing group for which to check whether the sequence identified by the given
     *                           {@code sequenceIdentifier}.
     * @param sequenceIdentifier The identifier of the sequence to validate whether it is contained in the queue.
     * @return The {@link PreparedStatement} used to check whether the given {@code sequenceIdentifier} is
     * {@link JdbcSequencedDeadLetterQueue#contains(Object) contained} in the queue.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement containsStatement(@Nonnull Connection connection,
                                        @Nonnull String processingGroup,
                                        @Nonnull String sequenceIdentifier) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the
     * {@link JdbcSequencedDeadLetterQueue#deadLetterSequence(Object)} operation.
     * <p>
     * As dead-letter sequences can be large, the {@link JdbcSequencedDeadLetterQueue} assumes it needs to page through
     * the result set. To that end it is recommended to use the given {@code offset} to define the starting point of the
     * query (for example by validate the {@link DeadLetterSchema#sequenceIndexColumn()}). The given {@code maxSize} can
     * be used to limit the result.
     *
     * @param connection         The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup    The processing group for which to retrieve a dead-letter sequence.
     * @param sequenceIdentifier The identifier of the sequence to retrieve.
     * @param offset             The offset from where to start the {@link PreparedStatement} under construction.
     * @param maxSize            The maximum size to limit the {@link PreparedStatement} under construction.
     * @return The {@link PreparedStatement} used to return the dead letter sequence for the given
     * {@code sequenceIdentifier} with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement letterSequenceStatement(@Nonnull Connection connection,
                                              @Nonnull String processingGroup,
                                              @Nonnull String sequenceIdentifier,
                                              int offset,
                                              int maxSize) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used to iterate over <b>all</b> sequences contained in the queue for the
     * {@link JdbcSequencedDeadLetterQueue#deadLetters()} operation.
     *
     * @param connection      The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup The processing group for which to retrieve all sequence identifiers.
     * @return The {@link PreparedStatement} used to return all sequence identifiers enqueued in the given
     * {@code processingGroup} with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement sequenceIdentifiersStatement(@Nonnull Connection connection,
                                                   @Nonnull String processingGroup) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the {@link JdbcSequencedDeadLetterQueue#size()} operation.
     *
     * @param connection      The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup The processing group for which to retrieve the size for.
     * @return The {@link PreparedStatement} used to retrieve the size with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement sizeStatement(@Nonnull Connection connection,
                                    @Nonnull String processingGroup) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the {@link JdbcSequencedDeadLetterQueue#sequenceSize(Object)}
     * operation.
     *
     * @param connection         The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup    The processing group for which to retrieve the size of the identified sequence.
     * @param sequenceIdentifier The identifier of the sequence for which to retrieve the size.
     * @return The {@link PreparedStatement} used to retrieve the size of the sequence identified by the given
     * {@code sequenceIdentifier} with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement sequenceSizeStatement(@Nonnull Connection connection,
                                            @Nonnull String processingGroup,
                                            @Nonnull String sequenceIdentifier) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the {@link JdbcSequencedDeadLetterQueue#amountOfSequences()}
     * operation.
     *
     * @param connection      The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup The processing group for which to retrieve the amount of sequences.
     * @return The {@link PreparedStatement} used to retrieve the amount of sequences with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement amountOfSequencesStatement(@Nonnull Connection connection,
                                                 @Nonnull String processingGroup) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used to retrieve the identifiers of the first entries of each sequence
     * with that can be claimed.
     * <p>
     * Used by the {@link JdbcSequencedDeadLetterQueue#process(Function)} and
     * {@link JdbcSequencedDeadLetterQueue#process(Predicate, Function)} operations. A row may be claimed if the
     * {@link DeadLetterSchema#processingStartedColumn() processing started} field is older than the given
     * {@code processingStartedLimit}.
     * <p>
     * The amount of sequences in a queue can be vast, hence the {@link JdbcSequencedDeadLetterQueue} assumes it needs
     * to page through the result set. To that end it is recommended to use the given {@code offset} to define the
     * starting point of the query (for example by validate the {@link DeadLetterSchema#sequenceIndexColumn()}). The
     * given {@code maxSize} can be used to limit the result.
     *
     * @param connection             The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup        The processing group to find claimable sequences for.
     * @param processingStartedLimit The {@link Instant} used to compare with the
     *                               {@link DeadLetterSchema#processingStartedColumn() processing started} field.
     * @param offset                 The offset from where to start the {@link PreparedStatement} under construction.
     * @param maxSize                The maximum size to limit the {@link PreparedStatement} under construction.
     * @return The {@link PreparedStatement} used to find the identifier of the first entries of each sequence that are
     * claimable.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement claimableSequencesStatement(@Nonnull Connection connection,
                                                  @Nonnull String processingGroup,
                                                  @Nonnull Instant processingStartedLimit,
                                                  int offset,
                                                  int maxSize) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used to claim a {@link DeadLetter} entry.
     * <p>
     * Claiming a {@code DeadLetter} ensure only a single thread
     * {@link JdbcSequencedDeadLetterQueue#process(Function) processes} the {@code DeadLetter}. This operation typically
     * updates the {@link DeadLetterSchema#processingStartedColumn() processing started} field with the given
     * {@code current} {@link Instant}, marking it as claimed for a certain timeframe.
     * <p>
     * The returned statement is used <em>after</em> the {@link JdbcSequencedDeadLetterQueue} searched for
     * {@link #claimableSequencesStatement(Connection, String, Instant, int, int) claimable sequences} during a
     * {@link JdbcSequencedDeadLetterQueue#process(Function)} or
     * {@link JdbcSequencedDeadLetterQueue#process(Predicate, Function)} invocation.
     *
     * @param connection             The {@link Connection} used to create the {@link PreparedStatement}.
     * @param identifier             The identifier of the {@link DeadLetter} to claim.
     * @param current                The {@link Instant} used to update the
     *                               {@link DeadLetterSchema#processingStartedColumn() processing started} field with to
     *                               mark it as claimed.
     * @param processingStartedLimit The {@link Instant} used to compare with the
     *                               {@link DeadLetterSchema#processingStartedColumn() processing started} field, to
     *                               ensure it wasn't claimed by another process.
     * @return The {@link PreparedStatement} used to claim a {@link DeadLetter} with for
     * {@link JdbcSequencedDeadLetterQueue#process(Function) processing}, ensuring no two threads are processing the
     * same letter.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement claimStatement(@Nonnull Connection connection,
                                     @Nonnull String identifier,
                                     @Nonnull Instant current,
                                     @Nonnull Instant processingStartedLimit) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used to retrieve the following {@link DeadLetter} from the sequence
     * identified with the given {@code sequenceIdentifier}. The returned statement is used <em>after</em> the
     * <p>
     * The returned statement is used <em>after</em> the {@link JdbcSequencedDeadLetterQueue}
     * {@link #claimStatement(Connection, String, Instant, Instant) claimed} a letter <b>and</b> successfully processed
     * it  during a {@link JdbcSequencedDeadLetterQueue#process(Function)} or
     * {@link JdbcSequencedDeadLetterQueue#process(Predicate, Function)} invocation.
     *
     * @param connection         The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup    The processing group for which to return the following {@link DeadLetter} in the
     *                           sequence identified by the given {@code sequenceIdentifier}.
     * @param sequenceIdentifier The identifier of the sequence for which to retrieve the following {@link DeadLetter}
     *                           for.
     * @param sequenceIndex      The index of the letter preceding the following {@link DeadLetter} to retrieve.
     * @return The {@link PreparedStatement} used to retrieve the {@link DeadLetter} in the sequence identified by the
     * given {@code sequenceIdentifier} with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement nextLetterInSequenceStatement(@Nonnull Connection connection,
                                                    @Nonnull String processingGroup,
                                                    @Nonnull String sequenceIdentifier,
                                                    long sequenceIndex) throws SQLException;

    /**
     * Constructs the {@link PreparedStatement} used for the {@link JdbcSequencedDeadLetterQueue#clear() clear}
     * operation.
     * <p>
     * Will only remove all entries for the given {@code processingGroup}.
     *
     * @param connection      The {@link Connection} used to create the {@link PreparedStatement}.
     * @param processingGroup The processing group for which to clear all entries.
     * @return The {@link PreparedStatement} used to {@link JdbcSequencedDeadLetterQueue#clear() clear} all entries for
     * the given {@code processingGroup} with.
     * @throws SQLException When the statement could not be created.
     */
    PreparedStatement clearStatement(@Nonnull Connection connection,
                                     @Nonnull String processingGroup) throws SQLException;
}
