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

package org.axonframework.messaging.eventhandling.annotation;

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;

/**
 * Interface indicating that a {@link MessageHandlingMember} is capable of handling specific event messages.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public interface EventHandlingMember<T> extends MessageHandlingMember<T> {

    /**
     * Returns the name of the event that can be handled.
     * <p>
     * Might be an empty {@link String} when undefined by this handling member, in which case components gathering
     * {@code EventHandlingMembers} should fall back to other mechanisms to define the name of a handling member.
     *
     * @return The name of the event that can be handled.
     */
    String eventName();
}