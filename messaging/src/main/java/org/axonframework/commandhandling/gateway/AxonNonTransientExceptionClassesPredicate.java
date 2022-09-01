package org.axonframework.commandhandling.gateway;

import org.axonframework.common.AxonNonTransientException;

/**
 * An Axon-specific {@link java.util.function.Predicate}, used to check the non-transiency of a failure comparing it against concrete classes.
 * <p/>
 * This implementation uses only {@link AxonNonTransientException} class for comparisons.
 * <p/>
 * This {@code Predicate} acts as the default for {@link RetryScheduler} instances.
 *
 * @author Damir Murat
 * @since 4.6.0
 */
public class AxonNonTransientExceptionClassesPredicate extends NonTransientExceptionClassesPredicate {
    public AxonNonTransientExceptionClassesPredicate() {
        super(AxonNonTransientException.class);
    }
}
