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
import net.sf.jsr107cache.Cache;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.RollbackConfiguration;
import org.axonframework.commandhandling.RollbackOnUncheckedExceptionConfiguration;
import org.axonframework.commandhandling.annotation.AnnotationCommandTargetResolver;
import org.axonframework.common.Assert;
import org.axonframework.common.NoCache;

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
    private RollbackConfiguration rollbackConfiguration;
    private boolean rescheduleCommandsOnCorruptState;
    private long coolingDownPeriod;
    private Cache cache;
    private final List<CommandHandlerInterceptor> invokerInterceptors = new ArrayList<CommandHandlerInterceptor>();
    private final List<CommandHandlerInterceptor> publisherInterceptors = new ArrayList<CommandHandlerInterceptor>();
    private CommandTargetResolver commandTargetResolver;
    private int invokerThreadCount = 1;
    private int publisherThreadCount = 1;

    /**
     * Initializes a configuration instance with default settings: ring-buffer size: 4096, blocking wait strategy and
     * multi-threaded claim strategy.
     */
    public DisruptorConfiguration() {
        this.claimStrategy = new MultiThreadedClaimStrategy(4096);
        this.waitStrategy = new BlockingWaitStrategy();
        coolingDownPeriod = 1000;
        cache = NoCache.INSTANCE;
        rescheduleCommandsOnCorruptState = true;
        rollbackConfiguration = new RollbackOnUncheckedExceptionConfiguration();
        commandTargetResolver = new AnnotationCommandTargetResolver();
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
    public DisruptorConfiguration setClaimStrategy(ClaimStrategy claimStrategy) { //NOSONAR (setter may hide field)
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
    public DisruptorConfiguration setWaitStrategy(WaitStrategy waitStrategy) { //NOSONAR (setter may hide field)
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
    public DisruptorConfiguration setExecutor(Executor executor) { //NOSONAR (setter may hide field)
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
    public DisruptorConfiguration setInvokerInterceptors(
            List<CommandHandlerInterceptor> invokerInterceptors) {  //NOSONAR (setter may hide field)
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
     * @param publisherInterceptors The interceptors to invoke when handling an incoming command
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setPublisherInterceptors(
            List<CommandHandlerInterceptor> publisherInterceptors) { //NOSONAR (setter may hide field)
        this.publisherInterceptors.clear();
        this.publisherInterceptors.addAll(publisherInterceptors);
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
    public DisruptorConfiguration setRollbackConfiguration(
            RollbackConfiguration rollbackConfiguration) { //NOSONAR (setter may hide field)
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
    public DisruptorConfiguration setRescheduleCommandsOnCorruptState(
            boolean rescheduleCommandsOnCorruptState) { //NOSONAR (setter may hide field)
        this.rescheduleCommandsOnCorruptState = rescheduleCommandsOnCorruptState;
        return this;
    }

    /**
     * Returns the cooling down period for the shutdown of the DisruptorCommandBus, in milliseconds. This is the time
     * in which new commands are no longer accepted, but the DisruptorCommandBus may reschedule Commands that may have
     * been executed against a corrupted Aggregate. If no commands have been rescheduled during this period, the
     * disruptor shuts down completely. Otherwise, it wait until no commands were scheduled for processing.
     *
     * @return the cooling down period for the shutdown of the DisruptorCommandBus, in milliseconds.
     */
    public long getCoolingDownPeriod() {
        return coolingDownPeriod;
    }

    /**
     * Sets the cooling down period in milliseconds. This is the time in which new commands are no longer accepted, but
     * the DisruptorCommandBus may reschedule Commands that may have been executed against a corrupted Aggregate. If no
     * commands have been rescheduled during this period, the disruptor shuts down completely. Otherwise, it wait until
     * no commands were scheduled for processing.
     * <p/>
     * Defaults to 1000 (1 second).
     *
     * @param coolingDownPeriod the cooling down period for the shutdown of the DisruptorCommandBus, in milliseconds.
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setCoolingDownPeriod(long coolingDownPeriod) { //NOSONAR (setter may hide field)
        this.coolingDownPeriod = coolingDownPeriod;
        return this;
    }

    /**
     * Returns the cache used to store Aggregates loaded by the DisruptorCommandBus.
     *
     * @return the cache used to store Aggregates
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * Sets the cache in which loaded aggregates will be stored. Aggregates that are not active in the CommandBus'
     * buffer will be loaded from this cache. If they are not in the cache, a new instance will be constructed using
     * Events from the Event Store.
     * <p/>
     * By default, no cache is used.
     *
     * @param cache The cache to store loaded aggregates in.
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setCache(Cache cache) { //NOSONAR (setter may hide field)
        this.cache = cache;
        return this;
    }

    /**
     * Returns the CommandTargetResolver that is used to find out which Aggregate is to be invoked for a given Command.
     *
     * @return the CommandTargetResolver that is used to find out which Aggregate is to be invoked for a given Command
     */
    public CommandTargetResolver getCommandTargetResolver() {
        return commandTargetResolver;
    }

    /**
     * Sets the CommandTargetResolver that must be used to indicate which Aggregate instance will be invoked by an
     * incoming command. The DisruptorCommandBus only uses this value if {@link #setInvokerThreadCount(int)
     * invokerThreadCount} or {@link #setPublisherThreadCount(int) publisherThreadCount} are greater than 1.
     * <p/>
     * Defaults to an {@link AnnotationCommandTargetResolver} instance.
     *
     * @param newCommandTargetResolver The CommandTargetResolver to use to indicate which Aggregate instance is target
     *                                 of an incoming Command
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setCommandTargetResolver(CommandTargetResolver newCommandTargetResolver) {
        this.commandTargetResolver = newCommandTargetResolver;
        return this;
    }

    /**
     * Returns the number of threads to use for Command Handler invocation.
     *
     * @return the number of threads to use for Command Handler invocation
     */
    public int getInvokerThreadCount() {
        return invokerThreadCount;
    }

    /**
     * Sets the number of Threads that should be used to invoke the Command Handlers. Defaults to 1.
     * <p/>
     * A good value for this setting mainly depends on the number of cores your machine has, as well as the amount of
     * I/O that the process requires. A good range, if no I/O is involved is <code>1 .. ([processor count] / 2)</code>.
     *
     * @param count The number of Threads to use for Command Handler invocation
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setInvokerThreadCount(int count) {
        Assert.isTrue(count > 0, "InvokerCount must be at least 1");
        this.invokerThreadCount = count;
        return this;
    }

    /**
     * Returns the number of threads to use for storing and publication of generated Events.
     *
     * @return the number of threads to use for storing and publication of generated Events
     */
    public int getPublisherThreadCount() {
        return publisherThreadCount;
    }

    /**
     * Sets the number of Threads that should be used to store and publish the generated Events. Defaults to 1.
     * <p/>
     * A good value for this setting mainly depends on the number of cores your machine has, as well as the amount of
     * I/O that the process requires. If no I/O is involved, a good starting value is <code>[processors / 2]</code>.
     *
     * @param count The number of Threads to use for publishing
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setPublisherThreadCount(int count) {
        Assert.isTrue(invokerThreadCount > 0, "PublisherCount must be at least 1");
        this.publisherThreadCount = count;
        return this;
    }
}
