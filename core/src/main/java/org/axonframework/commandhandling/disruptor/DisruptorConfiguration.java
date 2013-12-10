/*
 * Copyright (c) 2010-2012. Axon Framework
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
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import net.sf.jsr107cache.Cache;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.RollbackConfiguration;
import org.axonframework.commandhandling.RollbackOnUncheckedExceptionConfiguration;
import org.axonframework.commandhandling.annotation.AnnotationCommandTargetResolver;
import org.axonframework.common.Assert;
import org.axonframework.common.NoCache;
import org.axonframework.serializer.Serializer;
import org.axonframework.unitofwork.TransactionManager;

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

    /**
     * The default size of the buffer in this configuration
     */
    public static final int DEFAULT_BUFFER_SIZE = 4096;

    private int bufferSize;
    private ProducerType producerType;
    private WaitStrategy waitStrategy;
    private Executor executor;
    private RollbackConfiguration rollbackConfiguration;
    private boolean rescheduleCommandsOnCorruptState;
    private long coolingDownPeriod;
    private Cache cache;
    private final List<CommandHandlerInterceptor> invokerInterceptors = new ArrayList<CommandHandlerInterceptor>();
    private final List<CommandHandlerInterceptor> publisherInterceptors = new ArrayList<CommandHandlerInterceptor>();
    private final List<CommandDispatchInterceptor> dispatchInterceptors = new ArrayList<CommandDispatchInterceptor>();
    private TransactionManager transactionManager;
    private CommandTargetResolver commandTargetResolver;
    private int invokerThreadCount = 1;
    private int publisherThreadCount = 1;
    private int serializerThreadCount = 1;
    private Serializer serializer;
    private Class<?> serializedRepresentation = byte[].class;

    /**
     * Initializes a configuration instance with default settings: ring-buffer size: 4096, blocking wait strategy and
     * multi-threaded producer type.
     */
    public DisruptorConfiguration() {
        this.bufferSize = DEFAULT_BUFFER_SIZE;
        this.producerType = ProducerType.MULTI;
        this.waitStrategy = new BlockingWaitStrategy();
        coolingDownPeriod = 1000;
        cache = NoCache.INSTANCE;
        rescheduleCommandsOnCorruptState = true;
        rollbackConfiguration = new RollbackOnUncheckedExceptionConfiguration();
        commandTargetResolver = new AnnotationCommandTargetResolver();
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
        Assert.notNull(waitStrategy, "waitStrategy must not be null");
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
     * Returns the dispatch interceptors for the DisruptorCommandBus.
     *
     * @return the dispatch interceptors for the DisruptorCommandBus
     */
    public List<CommandDispatchInterceptor> getDispatchInterceptors() {
        return dispatchInterceptors;
    }

    /**
     * Configures the CommandDispatchInterceptor to use with the DisruptorCommandBus when commands are dispatched.
     * The interceptors are invoked by the thread that provides the commands to the command bus.
     *
     * @param dispatchInterceptors The dispatch interceptors to invoke when dispatching a command
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setDispatchInterceptors(
            List<CommandDispatchInterceptor> dispatchInterceptors) { //NOSONAR (setter may hide field)
        this.dispatchInterceptors.clear();
        this.dispatchInterceptors.addAll(dispatchInterceptors);
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
        Assert.notNull(rollbackConfiguration, "rollbackConfiguration may not be null");
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
     * invokerThreadCount}, {@link #setSerializerThreadCount(int) serializerThreadCount} or {@link
     * #setPublisherThreadCount(int) publisherThreadCount} is greater than 1.
     * <p/>
     * Defaults to an {@link AnnotationCommandTargetResolver} instance.
     *
     * @param newCommandTargetResolver The CommandTargetResolver to use to indicate which Aggregate instance is target
     *                                 of an incoming Command
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setCommandTargetResolver(CommandTargetResolver newCommandTargetResolver) {
        Assert.notNull(newCommandTargetResolver, "newCommandTargetResolver may not be null");
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
        Assert.isTrue(count > 0, "PublisherCount must be at least 1");
        this.publisherThreadCount = count;
        return this;
    }

    /**
     * Returns the configured number of threads that should perform the pre-serialization step. This value is ignored
     * unless a serializer is set using {@link #setSerializer(org.axonframework.serializer.Serializer)}.
     *
     * @return the number of threads to perform pre-serialization with
     */
    public int getSerializerThreadCount() {
        return serializerThreadCount;
    }

    /**
     * Sets the number of threads that should perform the pre-serialization step. This value is ignored
     * unless a serializer is set using {@link #setSerializer(org.axonframework.serializer.Serializer)}.
     *
     * @param newSerializerThreadCount the number of threads to perform pre-serialization with
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setSerializerThreadCount(int newSerializerThreadCount) {
        Assert.isTrue(newSerializerThreadCount >= 0, "SerializerThreadCount must be >= 0");
        this.serializerThreadCount = newSerializerThreadCount;
        return this;
    }

    /**
     * Returns the serializer to perform pre-serialization with, or <code>null</code> if no pre-serialization should be
     * done.
     *
     * @return the serializer to perform pre-serialization with, or <code>null</code> if no pre-serialization should be
     *         done
     */
    public Serializer getSerializer() {
        return serializer;
    }

    /**
     * Returns the serializer to perform pre-serialization with, or <code>null</code> if no pre-serialization should be
     * done. Defaults to <code>null</code>.
     *
     * @param newSerializer the serializer to perform pre-serialization with, or <code>null</code> if no
     *                      pre-serialization
     *                      should be done
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setSerializer(Serializer newSerializer) {
        this.serializer = newSerializer;
        return this;
    }

    /**
     * Indicates whether pre-serialization is configured. Is <code>true</code> when a serializer and at
     * least one thread is configured.
     *
     * @return whether pre-serialization is configured
     */
    public boolean isPreSerializationConfigured() {
        return serializer != null && serializerThreadCount > 0;
    }

    /**
     * Returns the type of data the serialized object should be represented in. Defaults to a byte array.
     *
     * @return the type of data the serialized object should be represented in
     */
    public Class<?> getSerializedRepresentation() {
        return serializedRepresentation;
    }

    /**
     * Sets the type of data the serialized object should be represented in. Defaults to a byte array
     * (<code>byte[]</code>).
     *
     * @param newSerializedRepresentation the type of data the serialized object should be represented in. May not be
     *                                    <code>null</code>.
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setSerializedRepresentation(Class<?> newSerializedRepresentation) {
        Assert.notNull(newSerializedRepresentation, "Serialized representation may not be null");
        this.serializedRepresentation = newSerializedRepresentation;
        return this;
    }

    /**
     * Returns the transaction manager to use to manage a transaction around the storage and publication of events.
     *
     * @return the transaction manager to use to manage a transaction around the storage and publication of events, or
     *         <code>null</code> if none is configured.
     */
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Sets the transaction manager to use to manage a transaction around the storage and publication of events.
     * The default (<code>null</code>) is to not have publication and storage of events wrapped in a transaction.
     *
     * @param newTransactionManager the transaction manager to use to manage a transaction around the storage and
     *                              publication of events
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setTransactionManager(TransactionManager newTransactionManager) {
        this.transactionManager = newTransactionManager;
        return this;
    }

    /**
     * Returns the buffer size to use.
     *
     * @return the buffer size to use.
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the buffer size to use.
     * The default is 4096.
     *
     * @param newBufferSize the buffer size to use
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setBufferSize(int newBufferSize) {
        this.bufferSize = newBufferSize;
        return this;
    }

    /**
     * Returns the producer type to use.
     *
     * @return the producer type to use.
     */
    public ProducerType getProducerType() {
        return producerType;
    }

    /**
     * Sets the producer type to use.
     * The default is to use a multi-threaded producer type.
     *
     * @param newProducerType the producer type to use
     * @return <code>this</code> for method chaining
     */
    public DisruptorConfiguration setProducerType(ProducerType newProducerType) {
        this.producerType = newProducerType;
        return this;
    }
}
