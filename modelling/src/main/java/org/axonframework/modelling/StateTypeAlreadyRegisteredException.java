/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling;

import org.axonframework.modelling.repository.AsyncRepository;

/**
 * Exception thrown by the {@link StateManager} when trying to register an {@link AsyncRepository} for a state type
 * that already has a repository registered.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class StateTypeAlreadyRegisteredException extends RuntimeException {

    /**
     * Initialize the exception with a message containing the given state type.
     *
     * @param stateType The state type for which no model was registered.
     */
    public StateTypeAlreadyRegisteredException(Class<?> stateType) {
        super("Cannot register repository for state type %s as one was already registered.".formatted(stateType.getName()));
    }
}
