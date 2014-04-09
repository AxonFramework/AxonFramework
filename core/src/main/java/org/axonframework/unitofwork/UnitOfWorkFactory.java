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

package org.axonframework.unitofwork;

/**
 * The <code>UnitOfWorkFactory</code> interface is used to obtain UnitOfWork instances to manage activity in command
 * handling processes.
 * <p/>
 * All UnitOfWork instances returned by this factory have been started. It is the responsibility of the caller to
 * either
 * invoke commit or rollback on these instances when done.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface UnitOfWorkFactory {

    /**
     * Creates a new UnitOfWork instance. The instance's {@link UnitOfWork#isStarted()} method returns
     * <code>true</code>.
     *
     * @return a new UnitOfWork instance, which has been started.
     */
    UnitOfWork createUnitOfWork();
}
