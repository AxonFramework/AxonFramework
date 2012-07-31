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

package org.axonframework.saga;

/**
 * Interface describing a mechanism that creates implementations of a Saga.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface SagaFactory {

    /**
     * Create a new instance of a Saga of given type. The Saga must be fully initialized and resources it depends on
     * must have been provided (injected or otherwise).
     *
     * @param sagaType The type of saga to create an instance for
     * @param <T>      The type of saga to create an instance for
     * @return A fully initialized instance of a saga of given type
     */
    <T extends Saga> T createSaga(Class<T> sagaType);

    /**
     * Indicates whether or not this factory can create instances of the given <code>sagaType</code>.
     *
     * @param sagaType The type of Saga
     * @return <code>true</code> if this factory can create instance of the given <code>sagaType</code>,
     *         <code>false</code> otherwise.
     */
    boolean supports(Class<? extends Saga> sagaType);
}
