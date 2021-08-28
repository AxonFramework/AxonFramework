package org.axonframework.commandhandling.gateway;

import org.axonframework.common.AxonNonTransientException;

/**
 * A default predicate for checking non-transiency of a failure comparing it against concrete classes.
 * <p/>
 * This default implementation uses only {@link AxonNonTransientException} class for comparisons.
 * <p/>
 * This predicate is intended to be used for configuring {@link RetryScheduler} implementations.
 */
public class DefaultNonTransientPredicate extends NonTransientClassesPredicate {
  public DefaultNonTransientPredicate() {
    super(AxonNonTransientException.class);
  }
}
