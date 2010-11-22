/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga.annotation;

import org.axonframework.saga.SagaLookupProperty;

import java.lang.reflect.Method;

/**
 * A data holder containing information of {@link SagaEventHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
class HandlerConfiguration {

    private static final HandlerConfiguration NO_HANDLER_CONFIGURATION = new HandlerConfiguration();

    /**
     * Returns a HandlerConfiguration indicating that a inspected method is *not* a SagaEventHandler.
     *
     * @return a HandlerConfiguration indicating that a inspected method is *not* a SagaEventHandler
     */
    public static HandlerConfiguration noHandler() {
        return NO_HANDLER_CONFIGURATION;
    }

    private final SagaCreationPolicy creationPolicy;
    private final Method handlerMethod;
    private final boolean endSaga;
    private final SagaLookupProperty lookupProperty;

    private HandlerConfiguration() {
        handlerMethod = null;
        endSaga = false;
        creationPolicy = SagaCreationPolicy.NONE;
        lookupProperty = null;
    }

    /**
     * Creates a HandlerConfiguration.
     *
     * @param creationPolicy The creation policy for the handlerMethod
     * @param handlerMethod  The method handling the event
     * @param endSaga        Whether or not this handler ends the saga
     * @param lookupProperty The property to find the saga instance with
     */
    public HandlerConfiguration(SagaCreationPolicy creationPolicy, Method handlerMethod, boolean endSaga,
                                SagaLookupProperty lookupProperty) {
        this.creationPolicy = creationPolicy;
        this.handlerMethod = handlerMethod;
        this.endSaga = endSaga;
        this.lookupProperty = lookupProperty;
    }

    /**
     * Indicates whether the inspected method is an Event Handler.
     *
     * @return true if the saga has a handler
     */
    public boolean isHandlerAvailable() {
        return handlerMethod != null;
    }

    /**
     * The LookupProperty to find the saga instance with.
     *
     * @return the LookupProperty to find the saga instance with
     */
    public SagaLookupProperty getLookupProperty() {
        return lookupProperty;
    }

    /**
     * Whether or not the inspected handler method ends the saga.
     *
     * @return true if the handler method ends the saga
     */
    public boolean isDestructorHandler() {
        return endSaga;
    }

    /**
     * Returns the creation policy of the inspected method.
     *
     * @return the creation policy of the inspected method
     */
    public SagaCreationPolicy getCreationPolicy() {
        return creationPolicy;
    }
}
