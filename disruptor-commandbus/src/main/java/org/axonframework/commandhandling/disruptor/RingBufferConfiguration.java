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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Configuration object for the DisruptorCommandBus. The RingBufferConfiguration provides access to the options to
 * tweak performance settings. Instances are not thread-safe and should not be altered after they have been used to
 * initialize a DisruptorCommandBus.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class RingBufferConfiguration {

    private ClaimStrategy claimStrategy;
    private WaitStrategy waitStrategy;
    private Executor executor;
    private final List<CommandHandlerInterceptor> interceptors = new ArrayList<CommandHandlerInterceptor>();

    /**
     * Initializes a configuration instance with default settings: ring-buffer size: 4096, blocking wait strategy and
     * multi-threaded claim strategy.
     */
    public RingBufferConfiguration() {
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
     *
     * @param claimStrategy The ClaimStrategy to use
     * @return <code>this</code> for method chaining
     *
     * @see com.lmax.disruptor.MultiThreadedClaimStrategy MultiThreadedClaimStrategy
     * @see com.lmax.disruptor.SingleThreadedClaimStrategy SingleThreadedClaimStrategy
     */
    public RingBufferConfiguration setClaimStrategy(ClaimStrategy claimStrategy) {
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
    public RingBufferConfiguration setWaitStrategy(WaitStrategy waitStrategy) {
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
     * DisruptorCommandBus. The provided executor must be capable of providing the required number of threads
     * (typically 3). Failure to do this results in the disruptor hanging at startup, waiting for resources to become
     * available.
     * <p/>
     * Defaults to <code>null</code>, causing the DisruptorCommandBus to create the necessary threads itself.
     *
     * @param executor the Executor that provides the processing resources
     * @return <code>this</code> for method chaining
     */
    public RingBufferConfiguration setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Returns the interceptors for the DisruptorCommandBus.
     *
     * @return the interceptors for the DisruptorCommandBus
     */
    public List<CommandHandlerInterceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * Configures the CommandHandlerInterceptors to use with the DisruptorCommandBus. The interceptors are invoked by
     * the thread that also executes the command handler.
     *
     * @param interceptors The interceptors to invoke when handling an incoming command
     * @return <code>this</code> for method chaining
     */
    public RingBufferConfiguration setInterceptors(List<CommandHandlerInterceptor> interceptors) {
        this.interceptors.clear();
        this.interceptors.addAll(interceptors);
        return this;
    }
}
