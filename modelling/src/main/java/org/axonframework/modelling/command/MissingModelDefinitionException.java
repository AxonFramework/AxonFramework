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

package org.axonframework.modelling.command;

/**
 * Exception thrown by the {@link ModelRegistry} when no model is registered for a given model class. Can be resolved by
 * registering a model for the given model class.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class MissingModelDefinitionException extends RuntimeException {

    /**
     * Initialize the exception with a message containing the given model class.
     *
     * @param modelClass The model class for which no model was registered
     */
    public MissingModelDefinitionException(Class<?> modelClass) {
        super("No Model was registered for the given model class: %s".formatted(modelClass.getName()));
    }
}
