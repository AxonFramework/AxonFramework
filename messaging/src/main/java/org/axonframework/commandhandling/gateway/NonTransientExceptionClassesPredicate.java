/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 *          new NonTransientExceptionClassesPredicate(
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
public class NonTransientExceptionClassesPredicate implements Predicate<Throwable> {
    private final List<Class<? extends Throwable>> nonTransientFailures;

    /**
     * Initialize the predicate with class(es) that are considered as non-transient.
     *
     * @param nonTransientFailures vararg list of non-transient class(es)
     */
    @SafeVarargs
    public NonTransientExceptionClassesPredicate(Class<? extends Throwable> ...nonTransientFailures) {
        this.nonTransientFailures = Arrays.asList(nonTransientFailures);
    }

    @Override
    public boolean test(Throwable failure) {
        return isNonTransientFailure(failure);
    }

    /**
     * Checks if the provided {@code failure} is considered non-transient.
     * <p/>
     * This implementation checks if any configured exception class is assignable to the provided failure.
     *
     * @param failure The failure to check for non-transiency
     * @return a boolean indicating if provided failure is non-transient ({@code true}) or not ({@code false})
     */
    protected boolean isNonTransientFailure(Throwable failure) {
        boolean isNonTransientFailure;

        isNonTransientFailure = getNonTransientFailures()
                .stream()
                .anyMatch(nonTransientFailure -> nonTransientFailure.isAssignableFrom(failure.getClass()));

        return isNonTransientFailure;
    }

    /**
     * Fetches a configured list of non-transient failures.
     * <p/>
     * Useful if one wants to override {@link #isNonTransientFailure(Throwable)}.
     *
     * @return a configured list of non-transient failures.
     */
    protected List<Class<? extends Throwable>> getNonTransientFailures() {
        return nonTransientFailures;
    }
}
