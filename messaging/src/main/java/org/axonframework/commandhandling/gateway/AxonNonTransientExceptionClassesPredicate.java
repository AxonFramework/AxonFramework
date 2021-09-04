package org.axonframework.commandhandling.gateway;

import org.axonframework.common.AxonNonTransientException;

/**
 * An Axon framework specific predicate for checking non-transiency of a failure comparing it against concrete classes.
 * <p/>
 * This implementation uses only {@link AxonNonTransientException} class for comparisons.
 * <p/>
 * This predicate is intended to be used for configuring {@link RetryScheduler} implementations where it is used as a
 * default if the user does not explicitly reconfigure it.
 *
 * @author Damir Murat
 * @since 4.6.0
 */
public class AxonNonTransientExceptionClassesPredicate extends NonTransientClassesPredicate {
  public AxonNonTransientExceptionClassesPredicate() {
    super(AxonNonTransientException.class);
  }
}
