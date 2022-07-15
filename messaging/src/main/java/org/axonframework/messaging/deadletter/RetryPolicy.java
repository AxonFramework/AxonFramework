package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

/**
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Milan Savic
 * @author Sara Pelligrini
 * @since 4.6.0
 */
@FunctionalInterface
public interface RetryPolicy<M extends Message<?>> {

    RetryDecision decide(RetryableDeadLetter<M> deadLetter, Throwable cause);

    // TODO: 12-07-22 should use something like the EvaluationTask, as otherwise users need to be smart enough to do UnitOfWork operations in the intended order and on the intended components.
    // TODO: 12-07-22 but should allow a form of predicate to decide when to execute the EvaluationTask. This likely means removal of the SchedulingDeadLetterQueue, since that purpose doesn't belong there.

    // TODO: 12-07-22 furthermore, this would eliminate release() style operations on the DLQ, as well as the onAvailable() callback on that level.
    // do we want this? Release does give additional power. But if the DLQ cannot do anything on a release, then it's rather stupid to contain it there.

    // TODO: 12-07-22 Should this also impose decision on what to do after evaluation? Right now, the EvaluationTask decides on this itself. With the responsibility, it'll decide when to retry and what to do after a retry.
    // pretty certain it shouldn't do this.
}
