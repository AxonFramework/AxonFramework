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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageType;

// should it be module or enhancer???
public class DefaultMessagePublishingModule<S extends BaseModule<S>> extends BaseModule<S>
        implements MessagePublishingModule<S>, MessagePublishingModule.MessageDefinitionsPhase<S> {

    /**
     * Construct a base module with the given {@code name}.
     *
     * @param name The name of this module. Must not be {@code null}.
     */
    DefaultMessagePublishingModule(@Nonnull String name) {
        super(name);
    }

    DefaultMessagePublishingModule(@Nonnull Module parent) {
        super(parent.name());
    }

    @Override
    public MessageDefinitionsPhase<S> publishingMessages() {
        return this;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        return null;
    }

    S self() {
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public S build() {
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public MessageDefinitionsPhase<S> define(Class<?> payloadType, MessageType messageType) {
        return null;
    }

    @Override
    public S and() {
        return null;
    }
}
