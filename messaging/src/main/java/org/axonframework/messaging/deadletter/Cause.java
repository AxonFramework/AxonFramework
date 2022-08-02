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

package org.axonframework.messaging.deadletter;

import java.io.Serializable;

/**
 * Contract describing the cause for {@link DeadLetter dead-lettering} a {@link org.axonframework.messaging.Message}.
 * These objects typically reflects a {@link Throwable}.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public interface Cause extends Serializable {

    /**
     * Returns the type of a dead-lettering cause. The {@code type} can, for example, reflect the fully qualified class
     * name of a {@link Throwable}.
     *
     * @return The type of this dead-lettering cause.
     */
    String type();

    /**
     * A message describing a cause for dead-lettering. The {@code message()} can, for example, reflect the message of a
     * {@link Throwable}.
     *
     * @return The message describing this cause's reason for dead-lettering.
     */
    String message();
}
