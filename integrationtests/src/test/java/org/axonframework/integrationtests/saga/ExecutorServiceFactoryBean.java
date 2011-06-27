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

package org.axonframework.integrationtests.saga;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Allard Buijze
 */
public class ExecutorServiceFactoryBean implements FactoryBean<ExecutorService>, InitializingBean, DisposableBean {

    private ExecutorService executorService;
    private BlockingQueue<Runnable> queue;

    @Override
    public ExecutorService getObject() throws Exception {
        return executorService;
    }

    @Override
    public Class<?> getObjectType() {
        return ExecutorService.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setQueue(BlockingQueue<Runnable> queue) {
        this.queue = queue;
    }

    @Override
    public void destroy() throws Exception {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (queue == null) {
            queue = new LinkedBlockingQueue<Runnable>();
        }
        executorService = new ThreadPoolExecutor(20, 20, 60, TimeUnit.SECONDS, queue);
    }
}
