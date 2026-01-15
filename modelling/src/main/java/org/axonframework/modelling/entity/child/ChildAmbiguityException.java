/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.entity.child;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Exception indicating that multiple child entities of a parent entity are able to handle the same command. This
 * happens if multiple {@link EntityChildMetamodel#supportedCommands()} contain the same
 * {@link QualifiedName}, as well as both child entities returning true for
 * {@link EntityChildMetamodel#canHandle(CommandMessage, Object, ProcessingContext)}, indicating that they have an active
 * child entity that can handle the command.
 * <p>
 * When this happens, make sure the {@link CommandTargetResolver} is configured correctly to resolve the child entity
 * that should handle the command.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ChildAmbiguityException extends RuntimeException {

    /**
     * Initializes the {@code ChildAmbiguityException} with the given {@code message}.
     *
     * @param message The message describing the cause of this exception.
     */
    public ChildAmbiguityException(@Nonnull String message) {
        super(message);
    }
}
