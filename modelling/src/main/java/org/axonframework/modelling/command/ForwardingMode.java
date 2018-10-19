/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.modelling.command.inspection.EntityModel;
import org.axonframework.messaging.Message;

import java.lang.reflect.Field;
import java.util.stream.Stream;

/**
 * Interface describing the required functionality to forward a message.
 * An example implementation is the {@link ForwardToAll}, which forwards all incoming messages.
 */
public interface ForwardingMode<T extends Message<?>> {

    /**
     * Initializes an instance of a {@link ForwardingMode}.
     *
     * @param field       The {@link java.lang.reflect.Field} to apply a ForwardingMode on. Provided to be able to check
     *                    for annotations attributes which might assist in the forwarding process.
     * @param childEntity A {@link EntityModel} constructed from the
     *                    given {@code field}.
     */
    default void initialize(Field field, EntityModel childEntity) {
    }

    /**
     * Filter the given {@link java.util.stream.Stream} of {@code candidates} which are to handle the supplied
     * {@code message}.
     *
     * @param message    The message of type {@code T} to be forwarded.
     * @param candidates The {@link java.util.stream.Stream} of candidates to filter.
     * @param <E>        The type of the {@code candidates}
     * @return a filtered {@link java.util.stream.Stream} of {@code candidates} which will handle the {@code message}.
     */
    <E> Stream<E> filterCandidates(T message, Stream<E> candidates);
}
