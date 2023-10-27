/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.List;

/**
 * Interface describing en entity that is a child of another entity.
 *
 * @param <T> defining the parent class this {@link ChildEntity} belongs to
 * @author Allard Buijze
 * @since 3.0
 */
public interface ChildEntity<T> {

    /**
     * Publish the given {@code msg} to the appropriate handlers on the given {@code declaringInstance}.
     *
     * @param msg               the message to publish
     * @param declaringInstance the instance of this entity to invoke handlers on
     */
    void publish(EventMessage<?> msg, T declaringInstance);

    /**
     * Returns the command handlers declared in this entity.
     *
     * @return a list of {@link MessageHandlingMember}s that are capable of processing command messages
     */
    List<MessageHandlingMember<? super T>> commandHandlers();
}
