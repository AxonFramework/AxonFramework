package org.axonframework.commandhandling.gateway;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * A predicate for checking non-transiency of a failure comparing it against configured concrete classes.
 * <p/>
 * Comparison checks if any of configured classes is assignable from a tested failure instance.
 * <p/>
 * This predicate is intended to be used for configuring {@link RetryScheduler} implementations like in the following example:
 * <pre>
 * IntervalRetryScheduler myRetryScheduler = IntervalRetryScheduler
 *      .builder()
 *      .retryExecutor(new ScheduledThreadPoolExecutor(1))
 *      .nonTransientFailurePredicate(
 *          new NonTransientClassesPredicate(
 *              AxonNonTransientException.class, NullPointerException.class, IllegalArgumentException.class,
 *              IllegalStateException.class
 *          )
 *      )
 *      .build();
 * </pre>
 *
 * @author Damir Murat
 * @since 4.6.0
 */
public class NonTransientClassesPredicate implements Predicate<Throwable> {
  private final List<Class<? extends Throwable>> nonTransientFailures;

  /**
   * Initialize the predicate with class(es) that are considered as non-transient.
   *
   * @param nonTransientFailures vararg list of non-transient class(es)
   */
  @SafeVarargs
  public NonTransientClassesPredicate(Class<? extends Throwable> ...nonTransientFailures) {
    this.nonTransientFailures = Arrays.asList(nonTransientFailures);
  }

  @Override
  public boolean test(Throwable failure) {
    boolean isNonTransientFailure;

    isNonTransientFailure = nonTransientFailures
        .stream()
        .anyMatch(nonTransientFailure -> nonTransientFailure.isAssignableFrom(failure.getClass()));

    return isNonTransientFailure;
  }
}
