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

/**
 * Exception indicating that while using the {@link PropertyBasedModelIdentifierResolver}, the indicated field can not be found.
 * This indicates a mismatch between the property configured in the {@link InjectModel#idProperty()} and the actual
 * payload class.
 *
 * @author Mitchell Herrijgers
 * @see PropertyBasedModelIdentifierResolver
 * @see InjectModel
 * @since 5.0.0
 */
public class TargetModelIdentifierMemberMismatchException extends RuntimeException {

    /**
     * Initialize the exception with the given {@code fieldName} that was not found in the payload of type
     * {@code payloadClass}.
     */
    public TargetModelIdentifierMemberMismatchException(@Nonnull String fieldName, @Nonnull Class<?> payloadClass) {
        super(String.format(
                "Could not find field [%s] or its accessor in payload of type [%s] as indicated on the @InjectModel annotation.",
                            fieldName,
                            payloadClass.getName()));
    }
}
