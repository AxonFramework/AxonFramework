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
 * @author Allard Buijze
 */
public class RingBufferConfiguration {

    private ClaimStrategy claimStrategy;
    private int bufferSize;
    private WaitStrategy waitStrategy;
    private Executor executor;
    private final List<CommandHandlerInterceptor> interceptors = new ArrayList<CommandHandlerInterceptor>();

    public RingBufferConfiguration() {
        this.bufferSize = 4096;
        this.claimStrategy = new MultiThreadedClaimStrategy(bufferSize);
        this.waitStrategy = new BlockingWaitStrategy();
    }

    public ClaimStrategy getClaimStrategy() {
        return claimStrategy;
    }

    public void setClaimStrategy(ClaimStrategy claimStrategy) {
        this.claimStrategy = claimStrategy;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    public void setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public List<CommandHandlerInterceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<CommandHandlerInterceptor> interceptors) {
        this.interceptors.clear();
        this.interceptors.addAll(interceptors);
    }
}
