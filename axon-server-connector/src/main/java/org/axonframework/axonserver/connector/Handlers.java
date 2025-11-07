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

package org.axonframework.axonserver.connector;

import java.util.Collection;
import java.util.function.BiPredicate;

/**
 * Handler registry - gives possibility of registering handlers for specific cases and contexts. Also, it gives various
 * ways of retrieving handlers.
 *
 * @param <Case>    the type of the case
 * @param <Handler> the type of the handler
 * @author Sara Pellegrini
 * @author Milan Savic
 * @since 4.2.1
 */
public interface Handlers<Case, Handler> {

    /**
     * Gets all handlers that match given {@code context} and {@code requestCase}.
     *
     * @param context     the context
     * @param requestCase the type of instruction to respond to
     * @return collection of corresponding handlers
     */
    Collection<Handler> get(String context, Case requestCase);

    /**
     * The same as {@link #get(String, Object)} but if no handler is found it returns provided {@code def} collection of
     * handlers.
     *
     * @param context     the context
     * @param requestCase the type of instruction to respond to
     * @param def         the default handlers if this registry does not contain any matching given criteria
     * @return collection of corresponding handlers
     */
    default Collection<Handler> getOrDefault(String context, Case requestCase, Collection<Handler> def) {
        Collection<Handler> handlers = get(context, requestCase);
        if (handlers.isEmpty()) {
            return def;
        }
        return handlers;
    }

    /**
     * Registers a {@code handler} that can handle instructions regardless of context or request case.
     *
     * @param handler the handler of the instruction
     */
    default void register(Handler handler) {
        register((c, rc) -> true, handler);
    }

    /**
     * Registers a {@code handler} that can handle instructions for given {@code context} regardless of request case.
     *
     * @param context the context
     * @param handler the handler of the instruction
     */
    default void register(String context, Handler handler) {
        register((c, rc) -> context.equals(c), handler);
    }

    /**
     * Registers a {@code handler} that can handle instructions for given {@code requestCase} regardless of context.
     *
     * @param requestCase the type of instruction to respond to
     * @param handler     the handler of the instruction
     */
    default void register(Case requestCase, Handler handler) {
        register((c, rc) -> requestCase.equals(rc), handler);
    }

    /**
     * Registers a {@code handler} that can handle instructions for given {@code context} and {@code requestCase}.
     *
     * @param context     the context
     * @param requestCase the type of instruction to respond to
     * @param handler     the handler of the instruction
     */
    default void register(String context, Case requestCase, Handler handler) {
        register((c, rc) -> context.equals(c) && requestCase.equals(rc), handler);
    }

    /**
     * Registers a {@code handler} that can handle instructions matching given {@code handlerSelector} predicate.
     *
     * @param handlerSelector selects a handler based on context and request case
     * @param handler         the handler of the instruction
     */
    void register(BiPredicate<String, Case> handlerSelector, Handler handler);
}
