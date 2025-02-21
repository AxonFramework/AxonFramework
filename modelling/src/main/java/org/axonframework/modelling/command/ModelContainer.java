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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A container of different models that can be accessed by type and name.
 * <p>
 * The combination of type and name should uniquely identify a model within the container. Retrieving a model by type
 * only requires a single model of that type to be present in the container.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface ModelContainer {

    /**
     * Retrieve a model of the given {@code modelType} from the container.
     * <p>
     * If multiple models of the given type are present in the container, this method will throw an exception. If no
     * model of the given type is present in the container, this method will throw an exception.
     *
     * @param modelType The type of model to retrieve
     * @param <T>       The type of model to retrieve
     * @return The model of the given {@code modelType}
     */
    default <T> T modelOf(@Nonnull Class<T> modelType) {
        return modelOf(modelType, null);
    }

    /**
     * Retrieve a model of the given {@code modelType} and {@code name} from the container.
     * <p>
     * If no model of the given type and name is present in the container, this method will throw an exception.
     *
     * @param modelType The type of model to retrieve
     * @param name      The name of the model to retrieve
     * @param <T>       The type of model to retrieve
     * @return The model of the given {@code modelType} and {@code name}
     */
    <T> T modelOf(@Nonnull Class<T> modelType, @Nullable String name);
}
