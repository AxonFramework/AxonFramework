/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.saga;

import org.axonframework.common.Assert;
import org.axonframework.common.Subscribable;
import org.axonframework.common.lock.IdentifierBasedLock;
import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.correlation.CorrelationDataProvider;
import org.axonframework.correlation.MultiCorrelationDataProvider;
import org.axonframework.correlation.SimpleCorrelationDataProvider;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.replay.ReplayAware;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import static java.lang.String.format;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 2.4.3
 * @see AbstractSynchronousSagaManager AbstractSynchronousSagaManager for the pre-2.4.3 version of this class
 */
public abstract class AbstractSagaManager<T extends Saga> implements SagaManager, Subscribable, ReplayAware {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    protected final EventBus eventBus;
    protected final Class<? extends T>[] sagaTypes;
    protected volatile boolean allowReplay = false;
    protected CorrelationDataProvider<? super EventMessage> correlationDataProvider = new SimpleCorrelationDataProvider();

    /**
     * Initializes the SagaManager with the given values.
     *
     * @param eventBus      The eventBus
     * @param sagaTypes      The types of Saga supported by this Saga Manager
     */
    protected AbstractSagaManager(EventBus eventBus,
                               Class<? extends T>... sagaTypes) {
        this.eventBus = eventBus;
        this.sagaTypes = Arrays.copyOf(sagaTypes, sagaTypes.length);
    }

    /**
     * Sets the correlation data provider for this SagaManager. It will provide the data to attach to messages sent by
     * Sagas managed by this manager.
     *
     * @param correlationDataProvider    the correlation data provider for this SagaManager
     */
    public void setCorrelationDataProvider(CorrelationDataProvider<? super EventMessage> correlationDataProvider) {
        this.correlationDataProvider = correlationDataProvider;
    }

    /**
     * Sets the given <code>correlationDataProviders</code>. Each will provide data to attach to messages sent by Sagas
     * managed by this manager. When multiple providers provide different values for the same key, the latter provider
     * will overwrite any values set earlier.
     *
     * @param correlationDataProviders the correlation data providers for this SagaManager
     */
    public void setCorrelationDataProviders(
            List<? extends CorrelationDataProvider<? super EventMessage>> correlationDataProviders) {
        this.correlationDataProvider = new MultiCorrelationDataProvider<EventMessage>(correlationDataProviders);
    }

    /**
     * Returns the set of Saga types managed by this instance.
     *
     * @return the set of Saga types managed by this instance.
     */
    @SuppressWarnings("unchecked")
    public Set<Class<? extends T>> getManagedSagaTypes() {
        return new LinkedHashSet<Class<? extends T>>(Arrays.asList(sagaTypes));
    }

    /**
     * @return true if this saga manager has been configured to allow replaying events.
     */
    public boolean isAllowReplay() {
        return allowReplay;
    }

    /**
     * Configure this saga manager to allow being placed in a ReplayingCluster. The default is false,
     * which will raise an exception should a replay be started.
     * @param allowReplay
     */
    public void setAllowReplay(boolean allowReplay) {
        this.allowReplay = allowReplay;
    }

    @Override
    public void beforeReplay() {
        if (!allowReplay) {
            throw new IllegalStateException(
                "SagaManager does not support replay. Attempting to replay cluster containing Saga "
                        + "event handlers will cause data corruption!");
        }
    }

    @Override
    public void afterReplay() {
    }

    @Override
    public void onReplayFailed(Throwable cause) {
    }
}
