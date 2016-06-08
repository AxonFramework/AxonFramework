/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.eventhandling.EventMessage;

import java.util.Map;

/**
 * Interface describing en entity that is a child of another entity.
 *
 * @param <T>
 */
public interface ChildEntity<T> {

    /**
     * Publish the given {@code msg} to the appropriate handlers on the given {@code declaringInstance}
     *
     * @param msg               The message to publish
     * @param declaringInstance The instance of this entity to invoke handlers on
     */
    void publish(EventMessage<?> msg, T declaringInstance);

    /**
     * Returns the commands and their respective handler that this entity declares
     *
     * @return a map containing with the Command Names as keys and the handlers as values.
     */
    Map<String, CommandMessageHandler<? super T>> commandHandlers();

}
