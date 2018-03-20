/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.queryhandling.responsetypes.ResponseType;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Helper list which makes usage of {@link QueryUpdateEmitter}s easier. If during emitting an update, emit fails or
 * there is an exception, this emitter will be removed from the list. Also, during completion, all emitters matching the
 * criteria will be removed from the list.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class QueryUpdateEmitterList {

    private final ConcurrentMap<String, CopyOnWriteArrayList<QueryUpdateEmitterDefinition<?>>> emitterDefinitions = new ConcurrentHashMap<>();

    /**
     * Adds given emitter to the list.
     *
     * @param queryName  the name of the query
     * @param updateType the incremental update type
     * @param emitter    the emitter
     * @param <U>        the incremental update type
     */
    public <U> void add(String queryName, Type updateType, QueryUpdateEmitter<U> emitter) {
        emitterDefinitions.computeIfAbsent(queryName, k -> new CopyOnWriteArrayList<>())
                          .addIfAbsent(new QueryUpdateEmitterDefinition<>(updateType, emitter));
    }

    /**
     * Finds the emitter matching the {@code queryName} and {@code updateType} and emits given {@code update} via them.
     * If some emitter gets disconnected or throws an exception, error will be reported using the same emitter and it
     * will be removed from the list.
     *
     * @param queryName  the name of the query
     * @param updateType the incremental update type
     * @param update     the actual update to be emitted
     * @param <U>        the incremental update type
     * @return {@code true} if emitting was successful to all emitters matching the criteria, otherwise returns {@code
     * false}. Also, {@code false} will be returned if there are no emitters matching the criteria
     */
    @SuppressWarnings("unchecked")
    public <U> boolean emit(String queryName, ResponseType<U> updateType, U update) {
        CopyOnWriteArrayList<QueryUpdateEmitterDefinition<?>> definitions = emitterDefinitions.getOrDefault(
                queryName,
                new CopyOnWriteArrayList<>());
        if (definitions.isEmpty()) {
            return false;
        }
        boolean success = true;
        for (QueryUpdateEmitterDefinition<?> definition : definitions) {
            if (updateType.matches(definition.getUpdateType())) {
                QueryUpdateEmitter<U> emitter = (QueryUpdateEmitter<U>) definition.getEmitter();
                if (!emit(emitter, update)) {
                    success = false;
                    definitions.remove(definition);
                }
            }
        }
        return success;
    }

    /**
     * Completes all the emitters matching the {@code queryName} and {@code updateType}. All of them will be removed
     * from the list.
     *
     * @param queryName  the name of the query
     * @param updateType the incremental update type
     * @param <U>        the incremental update type
     */
    public <U> void complete(String queryName, ResponseType<U> updateType) {
        CopyOnWriteArrayList<QueryUpdateEmitterDefinition<?>> definitions = emitterDefinitions.getOrDefault(queryName,
                                                                                                            new CopyOnWriteArrayList<>());
        for (QueryUpdateEmitterDefinition<?> definition : definitions) {
            if (updateType.matches(definition.getUpdateType())) {
                definition.getEmitter().complete();
                definitions.remove(definition);
            }
        }
    }

    /**
     * Filters emitter list based on {@code queryName} and {@code updateType}.
     *
     * @param queryName  the name of the query
     * @param updateType the type of the incremental update
     * @param <U>        the type of incremental update
     * @return list of emitters matching given {@code queryName} and {@code updateType}
     */
    @SuppressWarnings("unchecked")
    public <U> List<QueryUpdateEmitter<U>> filter(String queryName, ResponseType<U> updateType) {
        return emitterDefinitions.getOrDefault(queryName, new CopyOnWriteArrayList<>())
                                 .stream()
                                 .filter(definition -> updateType.matches(definition.getUpdateType()))
                                 .map(definition -> (QueryUpdateEmitter<U>) definition.getEmitter())
                                 .collect(Collectors.toList());
    }

    private <U> boolean emit(QueryUpdateEmitter<U> emitter, U update) {
        try {
            return emitter.emit(update);
        } catch (Exception e) {
            emitter.error(e);
            return false;
        }
    }

    private class QueryUpdateEmitterDefinition<U> {

        private final Type updateType;
        private final QueryUpdateEmitter<U> emitter;

        private QueryUpdateEmitterDefinition(Type updateType, QueryUpdateEmitter<U> emitter) {
            this.updateType = updateType;
            this.emitter = emitter;
        }

        private Type getUpdateType() {
            return updateType;
        }

        private QueryUpdateEmitter<U> getEmitter() {
            return emitter;
        }
    }
}
