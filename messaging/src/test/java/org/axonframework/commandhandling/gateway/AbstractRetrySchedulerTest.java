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

import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonNonTransientException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class AbstractRetrySchedulerTest {

    public static class RetrySchedulerStub extends AbstractRetryScheduler {

        protected RetrySchedulerStub(Builder builder) {
            super(builder);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected long computeRetryInterval(CommandMessage commandMessage, RuntimeException lastFailure, List<Class<? extends Throwable>[]> failures) {
            return 100;
        }

        static Builder builder() {
            return new Builder();
        }

        public static class Builder extends AbstractRetryScheduler.Builder<Builder> {
            RetrySchedulerStub build() {
                return new RetrySchedulerStub(this);
            }
        }
    }

    @Test
    void isExplicitlyNonTransient_defaults() {
        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub.builder().retryExecutor(mock(ScheduledExecutorService.class)).build();
        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new AxonNonTransientException("message") {}),
                "AxonNonTransientException should be treated as non-transient by default"
        );
    }

    @Test
    void isExplicitlyNonTransient_defaults_and_addNonTransientFailures() {
        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .addNonTransientFailurePredicate(new NonTransientExceptionClassesPredicate(CommandExecutionException.class, IllegalArgumentException.class))
                .build();

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new AxonNonTransientException("message") {}),
                "AxonNonTransientException should be treated as non-transient by default"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("message", null)),
                "Per configuration, CommandExecutionException should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("message", null)),
                "Per configuration, IllegalArgumentException should be treated as non-transient"
        );
    }

    @Test
    void isExplicitlyNonTransient_nonTransientFailurePredicate_throwable() {
        Predicate<Throwable> nonTransientFailurePredicate = (Throwable failure) -> "I'm non-transient failure".equals(failure.getMessage());

        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(nonTransientFailurePredicate)
                .build();

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new Exception()),
                "Per configuration, failures without appropriate message are not considered as non-transient"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new Exception("a message")),
                "Per configuration, failures without appropriate message are not considered as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new Exception("I'm non-transient failure")),
                "Per configuration, only failures with appropriate message should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new RuntimeException("a message", new NullPointerException("I'm non-transient failure"))),
                "Per configuration, only failures with appropriate message should be treated as non-transient"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new Exception("a message", new IllegalArgumentException("a message"))),
                "Per configuration, failures without appropriate message are not considered as non-transient"
        );
    }

    @Test
    void isExplicitlyNonTransient_nonTransientFailurePredicate_generics() {
        Predicate<IllegalArgumentException> nonTransientFailurePredicate = (failure) -> "I'm non-transient IllegalArgumentException failure".equals(failure.getMessage());

        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(IllegalArgumentException.class, nonTransientFailurePredicate)
                .build();

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException()),
                "Per configuration, IllegalArgumentException is transient only if their message says so"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("something")),
                "Per configuration, IllegalArgumentException is transient only if their message says so"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("I'm non-transient IllegalArgumentException failure")),
                "Per configuration, IllegalArgumentException with \"I'm non-transient IllegalArgumentException failure\" message should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("message", new IllegalArgumentException("I'm non-transient IllegalArgumentException failure"))),
                "Per configuration, IllegalArgumentException with \"I'm non-transient IllegalArgumentException failure\" message should be treated as non-transient"
        );

        // Here we are checking if non-castable failure types in predicate can cause problems - start
        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new RuntimeException()),
                "Per configuration, only IllegalArgumentException with appropriate message is treated as non-transient"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("a message", new RuntimeException())),
                "Per configuration, only IllegalArgumentException with appropriate message is treated as non-transient"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException(null, null)),
                "Per configuration, only IllegalArgumentException with appropriate message is treated as non-transient"
        );
        // Here we are checking if non-castable failure types in predicate can cause problems - end
    }

    @Test
    void isExplicitlyNonTransient_nonTransientFailurePredicate_and_addNonTransientFailures() {
        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(
                        // Not really needed (as this is a default), but added for clarity and explicitness.
                        new AxonNonTransientExceptionClassesPredicate()
                )
                .addNonTransientFailurePredicate(
                        new NonTransientExceptionClassesPredicate(CommandExecutionException.class, IllegalArgumentException.class)
                )
                .build();

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new AxonNonTransientException("message") {}),
                "Per configuration, AxonNonTransientException should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("message", null)),
                "Per configuration, CommandExecutionException should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("message", null)),
                "Per configuration, IllegalArgumentException should be treated as non-transient"
        );
    }

    @Test
    void isExplicitlyNonTransient_typicalUsageDemo() {
        Predicate<CommandExecutionException> nonTransientCommandExecutionExceptionPredicate =
                (CommandExecutionException failure) -> Objects.equals(failure.getDetails().orElse("transient"), "I'm non-transient");

        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(
                        new NonTransientExceptionClassesPredicate(
                                AxonNonTransientException.class, NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class
                        )
                )
                .addNonTransientFailurePredicate(CommandExecutionException.class, nonTransientCommandExecutionExceptionPredicate)
                .build();

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("failure", null, "I'm non-transient")),
                "Per configuration, CommandExecutionException with appropriate details should be treated as non-transient"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("failure", null, "a details")),
                "Per configuration, CommandExecutionException with unexpected details should be treated as transient"
        );

        Exception extendedFromCommandExecutionException = new CommandExecutionException("failure", null, "I'm non-transient") {};
        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(extendedFromCommandExecutionException),
                "Per configuration, CommandExecutionException descendants, with appropriate message, should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new NullPointerException("a null")),
                "Per configuration, NullPointerException should be treated as non-transient"
        );

        assertTrue(
                retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("illegal")),
                "Per configuration, IllegalArgumentException should be treated as non-transient"
        );

        assertFalse(
                retrySchedulerStub.isExplicitlyNonTransient(new RuntimeException("runtime problem")),
                "Per configuration, RuntimeException should be treated as a transient failure"
        );
    }
}
