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

/**
 * Exception thrown by the {@link StateManager} when trying to load an entity with an id of the wrong type.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class IdTypeMismatchException extends RuntimeException {

    /**
     * Initialize the exception with a message describing the mismatch between the wanted and actual id types.
     *
     * @param wantedIdType The type of id that was wanted.
     * @param providedIdType The type of id that was provided.
     */
    public IdTypeMismatchException(Class<?> wantedIdType, Class<?> providedIdType) {
        super("Can not load entity as provided id of type [%s] does not match wanted id type [%s].".formatted(
                providedIdType.getName(),
                wantedIdType.getName()));
    }
}
