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

package org.axonframework.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class LambdaBasedMessageTypeResolver implements MessageTypeResolver {

    private final String version;
    private final Customization customization;

    public LambdaBasedMessageTypeResolver() {
        this(MessageType.DEFAULT_VERSION, c -> c);
    }

    public LambdaBasedMessageTypeResolver(String version, UnaryOperator<Customization> customization) {
        this.version = version;
        this.customization = customization.apply(new Customization());
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        var resolver = customization.resolvers.get(payloadType);
        return resolver.resolve(payloadType);
    }

    public class Customization {

        private final Map<Class<?>, MessageTypeResolver> resolvers = new HashMap<>();

        public <T> Customization on(Class<T> payloadType, Function<T, MessageType> resolver) {
            resolvers.put(payloadType, (payload) -> resolver.apply(payloadType.cast(payload)));
            return this;
        }

//        public <T> Customization on(Class<T> payloadType, Function<T, QualifiedName> resolver) {
//            resolvers.put(payloadType,
//                          (payload) -> new MessageType(resolver.apply(payloadType.cast(payload)), version));
//            return this;
//        }
    }
}
