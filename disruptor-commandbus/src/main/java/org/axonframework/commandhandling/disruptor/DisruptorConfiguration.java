/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ClaimStrategy;
import com.lmax.disruptor.MultiThreadedClaimStrategy;
import com.lmax.disruptor.WaitStrategy;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.RollbackConfiguration;
import org.axonframework.commandhandling.RollbackOnUncheckedExceptionConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Configuration object for the DisruptorCommandBus. The DisruptorConfiguration provides access to the options to
 * tweak performance settings. Instances are not thread-safe and should not be altered after they have been used to
 * initialize a DisruptorCommandBus.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorConfiguration {

    private ClaimStrategy claimStrategy;
    private WaitStrategy waitStrategy;
    private Executor executor;
    private RollbackConfiguration rollbackConfiguration = new RollbackOnUncheckedExceptionConfiguration();
    private boolean rescheduleCommandsOnCorruptState = false;
    private final List<CommandHandlerInterceptor> invokerInterceptors = new ArrayList<CommandHandlerInterceptor>();
    private final List<CommandHandlerInterceptor> publisherInterceptors = new ArrayList<CommandHandlerInterceptor>();

    /**
     * Initializes a configuration instance with default settings: ring-buffer size: 4096, blocking wait strategy and
     * multi-threaded claim strategy.
     */
    public DisruptorConfiguration() {
        this.claimStrategy = new MultiThreadedClaimStrategy(4096);
        this.waitStrategy = new BlockingWaitStrategy();
    }

    /**
     * Returns the ClaimStrategy currently configured.
     *
     * @return the ClaimStrategy currently configured
     */
    public ClaimStrategy getClaimStrategy() {
        return claimStrategy;
    }

    /**
     * Sets the ClaimStrategy (including buffer size) which prescribes how threads get access to provide commands to
     * the CommandBus' RingBuffer.
     * <p/>
     * Defaults to a MultiThreadedClaimStrategy with 4096 elements in the RingBuffer.
     *
     * @param claimStrategy The ClaimStrategy to use
     * @return <code>this</code> for method chaining
     *
     * @see com.lmax.disruptor.MultiThreadedClaimStrategy MultiThreadedClaimStrategy
     * @see com.lmax.disruptor.SingleThreadedClaimStrategy SingleThreadedClaimStrategy
     */
    public DisruptorConfiguration setClaimStrategy(ClaimStrategy claimStrategy) {
        this.claimStrategy = claimStrategy;
        return this;
    }

    /**
     * Returns the WaitStrategy currently configured.
     *
     * @return the WaitStrategy currently configured
     */
    public WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    /**
     * Sets the <code>WaitStrategy</code>, which used to make dependent threads wait for tasks to be completed. The
     * choice of strategy mainly depends on the number of processors available and the number of tasks other than the
     * <code>DisruptorCommandBus</code> being processed.
     * <p/>
     * The <code>BusySpinWaitStrategy</code> provides the best throughput at the lowest latency, but also put a big
     * claim on available CPU resources. The <code>SleepingWaitStrategy</code> yields lower performance, but leaves
     * resources available for other processes to use.
     * <p/>
     * Defaults to the <code>BlockingWaitStrategy</code>.
     *
     * @param waitStrategy The WaitStrategy to use
     * @return <code>this</code> for method chaining
     *
     * @see com.lmax.disruptor.SleepingWaitStrategy SleepingWaitStrategy
     * @see com.lmax.disruptor.BlockingWaitStrategy BlockingWaitStrategy
     * @see com.lmax.disruptor.BusySpinWaitStrategy BusySpinWaitStrategy
     * @see com.lmax.disruptor.YieldingWaitStrategy YieldingWaitStrategy
     */
    public DisruptorConfiguration setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }

    /**
     * Returns the Executor providing the processing resources (Threads) for the DisruptorCommandBus.
     *
     * @return the Executor providing the processing resources
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the Executor that provides the processing resources (Threads) for the components of the
     * DisruptorCommandBus. The provided executor must be capable of providing the required number of threads. Three
     * threads are required immediately at startup and will not be returned until the CommandBus is stopped. Additional
     * threads are used to invoke callbacks and start a recovery process in case aggregate state has been corrupted.
     * Failure to do this results in the disruptor hanging at startup, waiting for resources to become available.
     * <p/>
     * Defaults to <code>null</code>, causing the DisruptorCommandBus to create the necessary threads itself. In that
     * case, threads are created in the <code>DisruptorCommandBus</code> ThreadGroup.
     *
     * @param executor the Executor that provides the processing resources
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Returns the interceptors for the DisruptorCommandBus.
     *
     * @return the interceptors for the DisruptorCommandBus
     */
    public List<CommandHandlerInterceptor> getInvokerInterceptors() {
        return invokerInterceptors;
    }

    /**
     * Configures the CommandHandlerInterceptors to use with the DisruptorCommandBus during in the invocation thread.
     * The interceptors are invoked by the thread that also executes the command handler.
     * <p/>
     * Note that this is *not* the thread that stores and publishes the generated events. See {@link
     * #setPublisherInterceptors(java.util.List)}.
     *
     * @param invokerInterceptors The interceptors to invoke when handling an incoming command
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setInvokerInterceptors(List<CommandHandlerInterceptor> invokerInterceptors) {
        this.invokerInterceptors.clear();
        this.invokerInterceptors.addAll(invokerInterceptors);
        return this;
    }

    /**
     * Returns the interceptors for the DisruptorCommandBus.
     *
     * @return the interceptors for the DisruptorCommandBus
     */
    public List<CommandHandlerInterceptor> getPublisherInterceptors() {
        return publisherInterceptors;
    }

    /**
     * Configures the CommandHandlerInterceptors to use with the DisruptorCommandBus during the publication of changes.
     * The interceptors are invoked by the thread that also stores and publishes the events.
     *
     * @param invokerInterceptors The interceptors to invoke when handling an incoming command
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setPublisherInterceptors(List<CommandHandlerInterceptor> invokerInterceptors) {
        this.publisherInterceptors.clear();
        this.publisherInterceptors.addAll(invokerInterceptors);
        return this;
    }

    /**
     * Returns the RollbackConfiguration indicating for which Exceptions the DisruptorCommandBus should perform a
     * rollback, and which exceptions should result in a Commit.
     * <p/>
     * Note that only exceptions resulting from Command processing are evaluated. Exceptions that occur while
     * attempting
     * to store or publish events will always result in a Rollback.
     *
     * @return the RollbackConfiguration indicating for the DisruptorCommandBus
     */
    public RollbackConfiguration getRollbackConfiguration() {
        return rollbackConfiguration;
    }

    /**
     * Sets the rollback configuration for the DisruptorCommandBus to use. Defaults to {@link
     * RollbackOnUncheckedExceptionConfiguration} a configuration that commits on checked exceptions, and performs a
     * rollback on runtime exceptions.
     *
     * @param rollbackConfiguration the RollbackConfiguration indicating for the DisruptorCommandBus
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setRollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
        this.rollbackConfiguration = rollbackConfiguration;
        return this;
    }

    /**
     * Indicates whether commands that failed due to potentially corrupt Aggregate state should be automatically
     * rescheduled for processing.
     *
     * @return <code>true</code> if commands are automatically rescheduled, otherwise <code>false</code>
     */
    public boolean getRescheduleCommandsOnCorruptState() {
        return rescheduleCommandsOnCorruptState;
    }

    /**
     * Indicates whether commands that failed because they were executed against potentially corrupted aggregate state
     * should be automatically rescheduled. Commands that caused the aggregate state to become corrupted are
     * <em>never</em> automatically rescheduled, to prevent poison message syndrome.
     * <p/>
     * Default to <code>true</code>.
     *
     * @param rescheduleCommandsOnCorruptState
     *         whether or not to automatically reschedule commands that failed due to potentially corrupted aggregate
     *         state.
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setRescheduleCommandsOnCorruptState(boolean rescheduleCommandsOnCorruptState) {
        this.rescheduleCommandsOnCorruptState = rescheduleCommandsOnCorruptState;
        return this;
    }
}
