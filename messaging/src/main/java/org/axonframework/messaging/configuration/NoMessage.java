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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;

import java.util.Map;

public class NoMessage implements Message<Void> {

    public static final NoMessage INSTANCE = new NoMessage();

    private static final QualifiedName NAME = new QualifiedName("axon", "no-message", "5.0.0");

    private NoMessage() {
    }

    @Override
    public String getIdentifier() {
        return "NO_MESSAGE";
    }

    @Nonnull
    @Override
    public QualifiedName name() {
        return NAME;
    }

    @Override
    public MetaData getMetaData() {
        return MetaData.emptyInstance();
    }

    @Override
    public Void getPayload() {
        return null;
    }

    @Override
    public Class<Void> getPayloadType() {
        return Void.class;
    }

    @Override
    public Message<Void> andMetaData(@Nonnull Map<String, ?> metaData) {
        return this;
    }

    @Override
    public Message<Void> withMetaData(@Nonnull Map<String, ?> metaData) {
        return this;
    }
}
