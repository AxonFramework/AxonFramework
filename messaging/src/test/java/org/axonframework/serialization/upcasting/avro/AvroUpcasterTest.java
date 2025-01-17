/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.serialization.upcasting.avro;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.eventhandling.AbstractSequencedDomainEventEntry;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.axonframework.serialization.avro.AvroSerializer;
import org.axonframework.serialization.avro.AvroSerializerStrategy;
import org.axonframework.serialization.avro.GenericRecordToByteArrayConverter;
import org.axonframework.serialization.avro.SpecificRecordBaseSerializerStrategy;
import org.axonframework.serialization.avro.test.ComplexObject;
import org.axonframework.serialization.avro.test.ComplexObjectSchemas;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.upcasting.event.SingleEventUpcaster;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Demonstrates and verifies behavior of avro based upcasters.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class AvroUpcasterTest {

    private final SchemaStore.Cache schemaStore = new SchemaStore.Cache();

    private final AvroSerializer serializer = AvroSerializer.builder()
            .serializerDelegate(JacksonSerializer.defaultSerializer())
            .revisionResolver(new AnnotationRevisionResolver())
            .schemaStore(schemaStore)
            .includeDefaultAvroSerializationStrategies(true)
            .includeSchemasInStackTraces(true)
            .build();

    /**
     * Purpose: Ensure that the complete wiring of serializer, initial-/intermediate representation
     * and upcaster works as expected. No Schema incompatibilities, we just modify a property here.
     */
    @Test
    void modifyPropertyOfComplexObjectInUpcaster() {
        schemaStore.addSchema(ComplexObject.getClassSchema());

        MetaData metaData = MetaData.with("key", "value");
        ComplexObject payload = ComplexObject.newBuilder()
                .setValue1("foo")
                .setValue2("bar")
                .setValue3(42)
                .build();

        GenericDomainEventMessage<?> msg = new GenericDomainEventMessage<>(
                ComplexObject.class.getCanonicalName(),
                "aggregateId",
                0,
                payload,
                metaData);

        EventData<?> eventData = new ByteArrayDomainEventEntry(msg, serializer);

        SetValue2OnComplexObjectUpcaster setValue2OnComplexObjectUpcaster = new SetValue2OnComplexObjectUpcaster("helloWorld");

        List<IntermediateEventRepresentation> collect = setValue2OnComplexObjectUpcaster.upcast(Stream.of(
                new InitialEventRepresentation(eventData, serializer)
        )).collect(Collectors.toList());
        Assertions.assertFalse(collect.isEmpty());
        IntermediateEventRepresentation r = collect.get(0);

        ComplexObject upcasted = serializer.deserialize(r.getData());

        assertEquals("helloWorld", upcasted.getValue2());
    }

    /**
     * Purpose: the  {@link org.axonframework.serialization.avro.test.ComplexObjectSchemas#incompatibleSchema}
     * does not define value1, so it is incompatible to the schema of {@link ComplexObject}. The Upcaster has
     * to add the additional field.
     */
    @Test
    void upcastIncompatibleSchema() {
        schemaStore.addSchema(ComplexObject.getClassSchema());
        schemaStore.addSchema(ComplexObjectSchemas.incompatibleSchema);

        // Given: a serialized event based on an old, incompatible schema (missing field "value1")
        GenericData.Record record = new GenericData.Record(ComplexObjectSchemas.incompatibleSchema);
        record.put("value2", "oldValue2");
        record.put("value3", 42);
        SerializedObject<byte[]> oldPayload = createSingleObjectEncodedSerializedObject(record, "1");

        // Then: we fail to deserialize without upcaster: incompatible schema, missing default for "value1"
        assertThrows(SerializationException.class, () -> serializer.deserialize(oldPayload));

        // When we implement an upcaster that knows the new reader Schema and can provide the missing field value
        SingleEventUpcaster setMissingValue1Upcaster = new SingleEventUpcaster() {
            @Override
            protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
                return true;
            }

            @Override
            protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
                return intermediateRepresentation.upcastPayload(
                        intermediateRepresentation.getType(),
                        GenericRecord.class,
                        oldRecord -> {
                            GenericRecord newRecord = new GenericData.Record(ComplexObject.getClassSchema());
                            newRecord.put("value1", "foo");
                            newRecord.put("value2", oldRecord.get("value2"));
                            newRecord.put("value3", oldRecord.get("value3"));
                            return newRecord;
                        }
                );
            }
        };


        // and apply it to the event stream
        SerializedObject<?> dataAfterUpcast = setMissingValue1Upcaster.upcast(Stream.of(new InitialEventRepresentation(
                eventData(oldPayload, serializer.serialize(MetaData.with("key", "value"), byte[].class)),
                serializer)
        )).collect(Collectors.toList()).get(0).getData();

        // Then: we can successfully deserialize to reader Class
        ComplexObject upcasted = serializer.deserialize(dataAfterUpcast);
        assertEquals("foo", upcasted.getValue1());
    }

    private static class SetValue2OnComplexObjectUpcaster extends SingleEventUpcaster {

        private final String newValue2;

        public SetValue2OnComplexObjectUpcaster(String newValue2) {
            this.newValue2 = newValue2;
        }


        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return true;
        }

        @Override
        protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return intermediateRepresentation.upcastPayload(
                    intermediateRepresentation.getType(),
                    GenericRecord.class,
                    it -> {
                        it.put("value2", newValue2);

                        return it;
                    }
            );
        }
    }

    private static class ByteArrayDomainEventEntry extends AbstractSequencedDomainEventEntry<byte[]> {

        public ByteArrayDomainEventEntry(DomainEventMessage<?> event, Serializer serializer) {
            super(event, serializer, byte[].class);
        }
    }

    private static EventData<byte[]> eventData(SerializedObject<byte[]> payload, SerializedObject<byte[]> metaData) {
        return new EventData<byte[]>() {
            @Override
            public String getEventIdentifier() {
                return UUID.randomUUID().toString();
            }

            @Override
            public Instant getTimestamp() {
                return Instant.now();
            }

            @Override
            public SerializedObject<byte[]> getMetaData() {
                return metaData;
            }

            @Override
            public SerializedObject<byte[]> getPayload() {
                return payload;
            }
        };

    }

    private static SerializedObject<byte[]> createSingleObjectEncodedSerializedObject(GenericRecord record, String revision) {
        return new SimpleSerializedObject<>(
                new GenericRecordToByteArrayConverter().convert(record),
                byte[].class,
                new SimpleSerializedType(record.getSchema().getFullName(), revision));
    }
}
