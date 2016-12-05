/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

/**
 * Implementation that simply throws the reported exception.
 *
 * @author Rene de Waele
 */
public enum ThrowingListenerErrorHandler implements ListenerErrorHandler {

    /**
     * Singleton error handler instance that throws exceptions without modification
     */
    INSTANCE;

    @Override
    public void onError(Exception exception, EventMessage<?> event, EventListener eventListener) throws Exception {
        throw exception;
    }
}
