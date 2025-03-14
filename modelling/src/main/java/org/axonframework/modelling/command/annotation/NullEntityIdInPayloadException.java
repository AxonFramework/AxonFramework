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

package org.axonframework.modelling.command.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.modelling.command.EntityIdResolver;

/**
 * Exception indicating that the payload of a message resolved to a {@code null} id. A non-null id is required to
 * resolve the entity from the {@link org.axonframework.modelling.StateManager}.
 *
 * @see EntityIdResolver
 * @see TargetEntityId
 * @since 5.0.0
 * @author Mitchell Herrijgers
 */
public class NullEntityIdInPayloadException extends RuntimeException {

    /**
     * Initialize the exception with the given payload of type {@code payloadClass}.
     *
     * @param payloadClass The type of the payload.
     */
    public NullEntityIdInPayloadException(@Nonnull Class<?> payloadClass) {
        super(String.format("The payload of type [%s] resolved to a id of null. A non-null id is required.",
                            payloadClass.getName()));
    }
}
