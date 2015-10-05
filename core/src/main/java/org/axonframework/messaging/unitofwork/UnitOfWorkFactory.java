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

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Subscription;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.CorrelationDataProvider;

/**
 * The <code>UnitOfWorkFactory</code> interface is used to obtain UnitOfWork instances to manage activity in command
 * handling processes.
 * <p/>
 * All UnitOfWork instances returned by this factory have been started. It is the responsibility of the caller to
 * either
 * invoke commit or rollback on these instances when done.
 *
 * @param <T> the type of {@link UnitOfWork} produced by this factory
 * @author Allard Buijze
 * @since 0.7
 */
public interface UnitOfWorkFactory<T extends UnitOfWork> {

    /**
     * Register given <code>correlationDataProvider</code> with the UnitOfWorkFactory. When a new UnitOfWork
     * instance is created the factory installs all registered correlation data providers with the unit of work.
     *
     * @param correlationDataProvider The correlation data provider to register
     * @return a handle to unregister the <code>correlationDataProvider</code>. When unregistered it will no longer
     * be attached to new unit of works.
     */
    Subscription registerCorrelationDataProvider(CorrelationDataProvider correlationDataProvider);

    /**
     * Creates a new UnitOfWork instance. The instance's {@link UnitOfWork#isActive()} method returns
     * <code>true</code>.
     *
     * @param message The message to be processed by the new Unit of Work
     * @return a new UnitOfWork instance, which has been started.
     */
    T createUnitOfWork(Message<?> message);
}
