/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.common.AxonException;

/**
 * Indicates that the {@link JpaSequencedDeadLetterQueue} could not resolve a converter based on the
 * {@link DeadLetterEntry} or {@link org.axonframework.eventhandling.EventMessage} that needed to be converted.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class NoJpaConverterFoundException extends AxonException {

    /**
     * Constructs a {@code NoJpaConverterFoundException} with the provided {@code message}.
     *
     * @param message The message containing more details about the cause.
     */
    public NoJpaConverterFoundException(String message) {
        super(message);
    }
}
