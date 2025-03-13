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
 * Exception indicating that no id was found in the payload of a message. Excactly one method or field annotated
 * with {@link TargetEntityId} is required to return a non-null value.
 *
 * @author Mitchell Herrijgers
 * @see EntityIdResolver
 * @see TargetEntityId
 * @since 5.0.0
 */
public class NoEntityIdFoundInPayload extends RuntimeException {

    /**
     * Initialize the exception with the given payload of type {@code payloadClass}.
     *
     * @param payloadClass The type of the payload not containing an id.
     */
    public NoEntityIdFoundInPayload(@Nonnull Class<?> payloadClass) {
        super(String.format(
                "No non-null @TargetEntityId annotation was found in payload of type [%s]. Exactly one identifier is required.",
                payloadClass.getName()));
    }
}
