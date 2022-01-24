/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.DeadLetterEvaluator;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.axonframework.messaging.deadletter.FixedDelayLetterEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link SimpleEventHandlerInvoker} utilizing a {@link DeadLetterQueue} to enqueue {@link
 * EventMessage} for which event handling failed.
 * <p>
 * This dead-lettering {@link EventHandlerInvoker} takes into account that events part of the same sequence (as
 * according to the {@link org.axonframework.eventhandling.async.SequencingPolicy}) should be enqueued in order.
 * TODO - add a bit about the evaluation process...
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetteringEventHandlerInvoker extends SimpleEventHandlerInvoker implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DeadLetterQueue<EventMessage<?>> queue;
    private final String processingGroup;
    private final boolean allowReset;
    private final DeadLetterEvaluator letterEvaluator;
    private final AtomicReference<RunState> runState;

    /**
     * Instantiate a dead-lettering {@link EventHandlerInvoker} based on the given {@link Builder builder}. Uses a
     * {@link DeadLetterQueue} to maintain and retrieve dead-letters from.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetteringEventHandlerInvoker} instance.
     */
    protected DeadLetteringEventHandlerInvoker(Builder builder) {
        super(builder);
        this.queue = builder.queue;
        this.processingGroup = builder.processingGroup;
        this.allowReset = builder.allowReset;
        this.letterEvaluator = builder.letterEvaluator;
        this.runState = new AtomicReference<>(RunState.initial(letterEvaluator::shutdown));
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * TODO add requirements and defaults
     *
     * @return A builder that can construct a {@link DeadLetteringEventHandlerInvoker}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void registerLifecycleHandlers(LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INBOUND_EVENT_CONNECTORS + 1, this::start);
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS + 1, this::shutdown);
    }

    /**
     * Starts the {@link ScheduledExecutorService} used by this invoker for evaluating dead-lettered events.
     */
    public void start() {
        RunState currentState = this.runState.updateAndGet(RunState::start);
        if (currentState.isRunning()) {
            letterEvaluator.start(() -> new EvaluationTask(super::invokeHandlers/*, add predicate*/));
        } else {
            // TODO: 21-01-22 fine tune exception
            throw new IllegalStateException("Starting this invoker is blocked somehow");
        }
    }

    /**
     * Shuts down the {@link ScheduledExecutorService} used by this invoker for evaluating dead-lettered events in a
     * friendly manner.
     *
     * @return A future that completes once this invoker's {@link ScheduledExecutorService} is properly shut down.
     */
    public CompletableFuture<Void> shutdown() {
        return runState.updateAndGet(RunState::attemptStop)
                       .shutdownHandle();
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        if (!super.sequencingPolicyMatchesSegment(message, segment)) {
            logger.trace("Ignoring event [{}] as it is not meant for segment [{}].", message, segment);
            return;
        }

        Object sequenceId = super.sequenceIdentifier(message);
        EventHandlingQueueIdentifier identifier = new EventHandlingQueueIdentifier(sequenceId, processingGroup);
        Optional<DeadLetterEntry<EventMessage<?>>> optionalEntry = queue.enqueueIfPresent(identifier, message);
        if (optionalEntry.isPresent()) {
            letterEvaluator.enqueued(optionalEntry.get());
            logger.info("Event [{}] is added to the dead-letter queue since its queue id [{}] was already present.",
                        message, identifier.combinedIdentifier());
        } else {
            try {
                logger.trace("Event [{}] with queue id [{}] is not present in the dead-letter queue present."
                                     + "Handle operation is delegated to the wrapped EventHandlerInvoker.",
                             message, identifier.combinedIdentifier());
                super.invokeHandlers(message);
            } catch (Exception e) {
                // TODO: 03-12-21 how to deal with the delegates ListenerInvocationErrorHandler in this case?
                //  It is mandatory to rethrow the exception, as otherwise the message isn't enqueued.
                // TODO: 14-01-22 We could (1) move the errorHandler invocation to a protected method to override,
                //  ensuring enqueue is invoked at all times with a try-catch block
                //  or (2) enforce a PropagatingErrorHandler at all times or (3) do nothing.
                DeadLetterEntry<EventMessage<?>> letter = queue.enqueue(identifier, message, e);
                letterEvaluator.enqueued(letter);
            }
        }
    }

    @Override
    public void performReset() {
        if (allowReset) {
            queue.clear(processingGroup);
        }
        super.performReset(null);
    }

    @Override
    public <R> void performReset(R resetContext) {
        if (allowReset) {
            queue.clear(processingGroup);
        }
        super.performReset(resetContext);
    }

    /**
     * Builder class to instantiate a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * TODO add requirements and defaults
     */
    public static class Builder extends SimpleEventHandlerInvoker.Builder<Builder> {

        private DeadLetterQueue<EventMessage<?>> queue;
        private String processingGroup;
        private boolean allowReset = false;
        // TODO: 24-01-22 should we even do a default?
        private DeadLetterEvaluator letterEvaluator = FixedDelayLetterEvaluator.defaultEvaluator();

        /**
         * Sets the {@link DeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         *
         * @param queue The {@link DeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder queue(DeadLetterQueue<EventMessage<?>> queue) {
            assertNonNull(queue, "The DeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the processing group name of this invoker.
         *
         * @param processingGroup The processing group name of this {@link EventHandlerInvoker}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder processingGroup(String processingGroup) {
            assertNonEmpty(processingGroup, "The processing group may not be null or empty");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Sets whether this {@link DeadLetteringEventHandlerInvoker} supports resets of the provided {@link
         * DeadLetterQueue}. If set to {@code true}, {@link DeadLetterQueue#clear(String)} will be invoked upon a {@link
         * #performReset()}/{@link #performReset(Object)} invocation. Defaults to {@code false}.
         *
         * @param allowReset A toggle dictating whether this {@link DeadLetteringEventHandlerInvoker} supports resets of
         *                   the provided {@link DeadLetterQueue}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder allowReset(boolean allowReset) {
            this.allowReset = allowReset;
            return this;
        }

        /**
         * @param letterEvaluator
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder letterEvaluator(DeadLetterEvaluator letterEvaluator) {
            assertNonNull(letterEvaluator, "The DeadLetterEvaluator may not be null");
            this.letterEvaluator = letterEvaluator;
            return this;
        }

        /**
         * Initializes a {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         *
         * @return A {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         */
        public DeadLetteringEventHandlerInvoker build() {
            return new DeadLetteringEventHandlerInvoker(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(queue, "The DeadLetterQueue is a hard requirement and should be provided");
            assertNonEmpty(processingGroup, "The processing group is a hard requirement and should be provided");
        }
    }

    /**
     *
     */
    private class EvaluationTask implements Runnable {

        private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

        private final HandlerInvoker handle;

        private EvaluationTask(HandlerInvoker handle) {
            // TODO: 06-12-21 introduce a 'release' mechanism for the queue/task as a solution for triggering actual evaluation
            this.handle = handle;
        }

        @Override
        public void run() {
            // TODO: 24-01-22 correctly remove the running task boolean when done
            if (runState.updateAndGet(RunState::attemptTaskStart).hasRunningTask()) {
                logger.debug("This runnable is stopped since an Evaluation Task is already running.");
                return;
            }

            Instant now = GenericEventMessage.clock.instant();
            boolean done = false;
            while (!done) {
                if (!runState.get().isRunning()) {
                    runState.get().shutdownHandle().complete(null);
                    return;
                }

                Optional<DeadLetterEntry<EventMessage<?>>> optionalLetter = queue.peek(processingGroup);
                if (!optionalLetter.isPresent()) {
                    logger.debug("Ending the evaluation task as there are no dead-letters for queue [{}] present.",
                                 processingGroup);
                    done = true;
                    continue;
                }

                DeadLetterEntry<EventMessage<?>> letter = optionalLetter.get();
                if (letter.expiresAt().isAfter(now)) {
                    logger.debug("Ending the evaluation task as there are no expired dead-letters for queue [{}].",
                                 processingGroup);
                    done = true;
                    continue;
                }

                try {
                    // TODO: 03-12-21 This will require us to store the segment with the entry too
                    //delegate.handle(letter.message(), letter.queueIdentifier().segment());
                    // TODO: 17-01-22 Do we care about the Segment at this stage?
                    //  A different thread, separate from regular event handling, is performing this task any how.
                    //  From that end, I anticipate it to be okay if anyone just picks it up, regardless of the Segment.
                    handle.invokerHandlers(letter.message());
                    letter.acknowledge();
                    logger.info("Dead-letter [{}] is released as it is successfully handled for processing group [{}].",
                                letter.message().getIdentifier(), processingGroup);
                } catch (Exception e) {
                    logger.info("Failed handling dead-letter [{}] for processing group [{}].",
                                letter.message().getIdentifier(), processingGroup);
                    done = true;
                }
            }
        }
    }

    /**
     * A wrapper around a {@link Consumer} of the {@link EventMessage} to pass to the {@link EvaluationTask}. This
     * solutions allows rethrowing any exceptions thrown by the event handlers.
     */
    @FunctionalInterface
    private interface HandlerInvoker extends Consumer<EventMessage<?>> {

        void invokerHandlers(EventMessage<?> event) throws Exception;

        @Override
        default void accept(EventMessage<?> eventMessage) {
            try {
                invokerHandlers(eventMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Run state container for this invoker. Maintains whether this invoker {@link #isRunning()}, has a {@link
     * #taskRunning}
     */
    private static class RunState {

        private final boolean isRunning;
        private final boolean taskRunning;
        private final CompletableFuture<Void> shutdownHandle;
        private final Runnable shutdownAction;

        private RunState(boolean isRunning,
                         boolean taskRunning,
                         CompletableFuture<Void> shutdownHandle,
                         Runnable shutdownAction) {
            this.isRunning = isRunning;
            this.taskRunning = taskRunning;
            this.shutdownHandle = shutdownHandle;
            this.shutdownAction = shutdownAction;
        }

        public static RunState initial(Runnable shutdownAction) {
            return new RunState(false, false, CompletableFuture.completedFuture(null), shutdownAction);
        }

        public RunState start() {
            return new RunState(true, taskRunning, null, shutdownAction);
        }

        public RunState attemptTaskStart() {
            // Starts a task if the invoker is running and no task is active. Otherwise, return the state as is.
            return isRunning && !taskRunning
                    ? new RunState(true, true, shutdownHandle, shutdownAction)
                    : this;
        }

        public RunState attemptStop() {
            if (!isRunning || shutdownHandle != null) {
                // It's already stopped
                return this;
            } else if (taskRunning) {
                // A task is active, so we wait for it to complete the shutdown handle
                CompletableFuture<Void> handle = new CompletableFuture<>();
                handle.whenComplete((r, e) -> shutdownAction.run());
                return new RunState(false, false, handle, shutdownAction);
            } else {
                // There's no task active, so we can immediately complete the future and invoke the shutdown action.
                return new RunState(false, false, CompletableFuture.runAsync(shutdownAction), shutdownAction);
            }
        }

        public boolean isRunning() {
            return isRunning;
        }

        public boolean hasRunningTask() {
            return taskRunning;
        }

        public CompletableFuture<Void> shutdownHandle() {
            return shutdownHandle;
        }
    }
}
