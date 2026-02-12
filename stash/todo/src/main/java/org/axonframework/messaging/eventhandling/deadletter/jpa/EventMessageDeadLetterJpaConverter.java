/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ClassUtils;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.SimpleEntry;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converter responsible for converting to and from {@link EventMessage} implementations for storage in a
 * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue}.
 * <p>
 * Serializes configured {@link Context} resources into a single context-resources blob and restores them when
 * deserializing. Legacy aggregate/token columns are ignored by this converter.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class EventMessageDeadLetterJpaConverter implements DeadLetterJpaConverter<EventMessage> {

    private final Set<Context.ResourceKey<?>> serializableResources;
    private final Map<String, Context.ResourceKey<?>> resourcesByLabel;

    /**
     * Constructs a converter with no serializable resources configured.
     */
    public EventMessageDeadLetterJpaConverter() {
        this(Set.of());
    }

    /**
     * Constructs a converter with the given serializable {@link Context.ResourceKey ResourceKeys}.
     *
     * @param serializableResources The resource keys to serialize from the context.
     */
    public EventMessageDeadLetterJpaConverter(Set<Context.ResourceKey<?>> serializableResources) {
        this.serializableResources = Set.copyOf(serializableResources);
        this.resourcesByLabel = new HashMap<>();
        for (Context.ResourceKey<?> key : this.serializableResources) {
            if (key.label() != null) {
                resourcesByLabel.put(key.label(), key);
            }
        }
    }

    @Override
    public @Nonnull DeadLetterEventEntry convert(@Nonnull EventMessage message,
                                                 @Nullable Context context,
                                                 @Nonnull EventConverter eventConverter,
                                                 @Nonnull Converter genericConverter) {
        // Serialize payload and metadata
        byte[] serializedPayload = eventConverter.convert(message.payload(), byte[].class);
        byte[] serializedMetadata = eventConverter.convert(message.metadata(), byte[].class);

        // Serialize configured context resources
        Context sourceContext = context != null ? context : Context.empty();
        byte[] serializedContextResources = serializeContextResources(sourceContext, genericConverter);

        return new DeadLetterEventEntry(
                GenericEventMessage.class.getName(),
                message.identifier(),
                message.type().toString(),
                message.timestamp().toString(),
                message.payloadType().getName(),
                null, // revision not tracked separately in AF5
                serializedPayload,
                serializedMetadata,
                null,
                null,
                null,
                null,
                null,
                serializedContextResources
        );
    }

    @Nonnull
    @Override
    public MessageStream.Entry<EventMessage> convert(@Nonnull DeadLetterEventEntry entry,
                                                     @Nonnull EventConverter eventConverter,
                                                     @Nonnull Converter genericConverter) {
        // Deserialize payload and metadata
        Object payload = eventConverter.convert(entry.getPayload(), ClassUtils.loadClass(entry.getPayloadType()));
        @SuppressWarnings("unchecked")
        Map<String, String> metadataMap = eventConverter.convert(entry.getMetadata(), Map.class);
        Metadata metadata = Metadata.from(metadataMap);

        // Create GenericEventMessage
        EventMessage message = new GenericEventMessage(
                entry.getEventIdentifier(),
                MessageType.fromString(entry.getType()),
                payload,
                metadata,
                Instant.parse(entry.getTimeStamp())
        );

        // Build context with restored resources
        Context context = Context.empty();
        context = restoreContextResources(entry.getContextResources(), genericConverter, context);

        return new SimpleEntry<>(message, context);
    }

    private byte[] serializeContextResources(Context context, Converter genericConverter) {
        if (serializableResources.isEmpty()) {
            return null;
        }
        List<SerializedContextResource> resources = new ArrayList<>();
        for (Context.ResourceKey<?> key : serializableResources) {
            Object resource = context.getResource(key);
            if (resource != null) {
                resources.add(new SerializedContextResource(
                        key.label(),
                        resource.getClass().getName(),
                        genericConverter.convert(resource, byte[].class)
                ));
            }
        }
        if (resources.isEmpty()) {
            return null;
        }
        return genericConverter.convert(resources.toArray(new SerializedContextResource[0]), byte[].class);
    }

    private Context restoreContextResources(byte[] serializedResources,
                                            Converter genericConverter,
                                            Context context) {
        if (serializedResources == null || resourcesByLabel.isEmpty()) {
            return context;
        }
        SerializedContextResource[] resources =
                genericConverter.convert(serializedResources, SerializedContextResource[].class);
        for (SerializedContextResource resource : resources) {
            if (resource == null || resource.label == null) {
                continue;
            }
            Context.ResourceKey<?> key = resourcesByLabel.get(resource.label);
            if (key == null) {
                continue;
            }
            Class<?> type = ClassUtils.loadClass(resource.type);
            Object value = genericConverter.convert(resource.data, type);
            //noinspection unchecked
            context = context.withResource((Context.ResourceKey<Object>) key, value);
        }
        return context;
    }

    /**
     * Simple DTO for serializing context resources.
     */
    public static class SerializedContextResource {

        private String label;
        private String type;
        private byte[] data;

        @SuppressWarnings("unused")
        public SerializedContextResource() {
            // default constructor for serialization
        }

        public SerializedContextResource(String label, String type, byte[] data) {
            this.label = label;
            this.type = type;
            this.data = data;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }

        public byte[] getData() {
            return data;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SerializedContextResource that = (SerializedContextResource) o;
            return Objects.equals(label, that.label)
                    && Objects.equals(type, that.type)
                    && java.util.Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(label, type);
            result = 31 * result + java.util.Arrays.hashCode(data);
            return result;
        }
    }
}
