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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

public class AnnotationBasedCommandHandlingComponent<T> implements CommandHandlingComponent {

    private AnnotationBasedCommandHandlingComponent(T instance) {
        // Inspect methods
    }

    public static <T> AnnotationBasedCommandHandlingComponent<T> forInstance(T instance) {
        return new AnnotationBasedCommandHandlingComponent<>(instance);
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return Set.of();
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        return MessageStream.empty();
    }
}
