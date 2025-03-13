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

import java.util.List;

/**
 * Exception indicating that multiple identifiers were found in the payload of a message. Only one
 * method or field annotated with {@link TargetEntityId} is allowed to return a non-null value.
 *
 * @see EntityIdResolver
 * @see TargetEntityId
 * @since 5.0.0
 * @author Mitchell Herrijgers
 */
public class MultipleTargetEntityIdsFoundInPayload extends RuntimeException {

    /**
     * Initialize the exception with the given {@code identifiers} found in the payload of type {@code payloadClass}.
     *
     * @param identifiers  The identifiers found in the payload.
     * @param payloadClass The type of the payload.
     */
    public MultipleTargetEntityIdsFoundInPayload(@Nonnull List<Object> identifiers, @Nonnull Class<?> payloadClass) {
        super(String.format("Found multiple identifiers in payload of type [%s]: %s. Only one identifier is allowed.",
                            payloadClass.getName(), identifiers));
    }
}
