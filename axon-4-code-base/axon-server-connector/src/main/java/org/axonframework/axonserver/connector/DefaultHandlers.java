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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link Handlers}. Uses a map to hold a registry of handlers.
 *
 * @param <Case>    the type of the case
 * @param <Handler> the type of the handler
 * @author Sara Pellegrini, Milan Savic
 * @since 4.2.1
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class DefaultHandlers<Case, Handler> implements Handlers<Case, Handler> {

    private final Map<BiPredicate<String, Case>, Handler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public Collection<Handler> get(String context, Case requestCase) {
        return handlerMap.entrySet()
                         .stream()
                         .filter(e -> e.getKey().test(context, requestCase))
                         .map(Map.Entry::getValue)
                         .collect(Collectors.toList());
    }

    @Override
    public void register(BiPredicate<String, Case> handlerSelector, Handler handler) {
        handlerMap.put(handlerSelector, handler);
    }
}
