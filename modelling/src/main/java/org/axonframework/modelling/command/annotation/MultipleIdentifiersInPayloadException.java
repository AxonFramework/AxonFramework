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

import org.axonframework.modelling.command.ModelIdResolver;

import java.util.List;

/**
 * Exception indicating that multiple identifiers were found in the payload of a message, while only one was expected.
 * Models can only have one identifier. If your model has a composite identifier, you should use a single field or
 * method to represent the composite identifier. Alternatively, you can use a custom {@link ModelIdResolver} to resolve
 * the identifier from the payload.
 *
 * @see ModelIdResolver
 * @see TargetModelIdentifier
 * @since 5.0.0
 * @author Mitchell Herrijgers
 */
public class MultipleIdentifiersInPayloadException extends RuntimeException {

    /**
     * Initialize the exception with the given {@code identifiers} found in the payload of type {@code payloadClass}.
     *
     * @param identifiers  The identifiers found in the payload
     * @param payloadClass The type of the payload
     */
    public MultipleIdentifiersInPayloadException(List<Object> identifiers, Class<?> payloadClass) {
        super(String.format("Found multiple identifiers in payload of type [%s]: %s. Only one identifier is allowed.",
                            payloadClass.getName(), identifiers));
    }
}
