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

import jakarta.annotation.Nonnull;

public class NamespaceMessageTypeResolver implements MessageTypeResolver {

    private final String namespace;
    private final SimpleMessageTypeResolver simple;

    public NamespaceMessageTypeResolver(String namespace) {
        this(namespace, new SimpleMessageTypeResolver());
    }

    private NamespaceMessageTypeResolver(String namespace, SimpleMessageTypeResolver simple) {
        this.namespace = namespace;
        this.simple = simple;
    }

    public NamespaceMessageTypeResolver message(@Nonnull Class<?> payloadType, @Nonnull String localName, @Nonnull String version) {
        return new NamespaceMessageTypeResolver(
                this.namespace,
                this.simple.message(
                        payloadType,
                        new MessageType(this.namespace, localName, version)
                )
        );
    }

    public NamespaceMessageTypeResolver namespace(@Nonnull String namespace) {
        return new NamespaceMessageTypeResolver(namespace, this.simple);
    }

    @Override
    public MessageType resolve(Class<?> payloadType) {
        return simple.resolve(payloadType);
    }

}
