package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonNonTransientException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
  void isExplicitlyNonTransient_configuredNonTransientFailures() {
    RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
        .builder()
        .retryExecutor(mock(ScheduledExecutorService.class))
        .nonTransientFailures(Arrays.asList(AxonNonTransientException.class, CommandExecutionException.class))
        .build();

    assertTrue(
        retrySchedulerStub.isExplicitlyNonTransient(new AxonNonTransientException("message") {}),
        "Per configuration, AxonNonTransientException should be treated as non-transient"
    );

    assertTrue(
        retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("message", null)),
        "Per configuration, CommandExecutionException should be treated as non-transient"
    );
  }

  @Test
  void isExplicitlyNonTransient_configuredNonTransientFailurePredicate() {
    Predicate<Throwable> nonTransientFailurePredicate = (Throwable failure) -> false;

    RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
        .builder()
        .retryExecutor(mock(ScheduledExecutorService.class))
        .nonTransientFailurePredicate(nonTransientFailurePredicate)
        .build();

    assertFalse(
        retrySchedulerStub.isExplicitlyNonTransient(new AxonNonTransientException("message") {}),
        "With nonTransientFailurePredicate configured, default nonTransientFailures are ignored and " +
        "AxonNonTransientException should be treated as transient"
    );
  }

  @Test
  void isExplicitlyNonTransient_usingNonTransientFailurePredicate() {
    Predicate<Throwable> nonTransientFailurePredicate = (Throwable failure) -> {
      if (CommandExecutionException.class.isAssignableFrom(failure.getClass())) {
        CommandExecutionException commandExecutionException = (CommandExecutionException) failure;
        return Objects.equals(commandExecutionException.getDetails().orElse("transient"), "I'm non-transient");
      }

      return false;
    };

    RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
        .builder()
        .retryExecutor(mock(ScheduledExecutorService.class))
        .nonTransientFailurePredicate(nonTransientFailurePredicate)
        .build();

    assertTrue(
        retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("failure", null, "I'm non-transient")),
        "CommandExecutionException should be treated as non-transient"
    );

    Exception extendedFromCommandExecutionException = new CommandExecutionException("failure", null, "I'm non-transient") {};
    assertTrue(
        retrySchedulerStub.isExplicitlyNonTransient(extendedFromCommandExecutionException),
        "CommandExecutionException descendants should be treated as non-transient"
    );
  }
}
