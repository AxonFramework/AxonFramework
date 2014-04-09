/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.EventHandler;
import org.axonframework.domain.EventMessage;
import org.axonframework.serializer.SerializationAware;
import org.axonframework.serializer.Serializer;

/**
 * Disruptor Event Handler that serializes EventMessage implementations that are also SerializationAware.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializerHandler implements EventHandler<CommandHandlingEntry> {

    private final Serializer serializer;
    private final int serializerId;
    private final Class<?> serializedRepresentation;

    /**
     * Initialize the handler using given <code>serializer</code> and processing the segment Id with given
     * <code>serializerId</code> (in case of multiple serializer threads).
     *
     * @param serializer               The serializer to pre-serialize with
     * @param serializerSegmentId      The segment of this instance to handle
     * @param serializedRepresentation The representation to which to serialize the payload and meta data
     */
    public SerializerHandler(Serializer serializer, int serializerSegmentId, Class<?> serializedRepresentation) {
        this.serializer = serializer;
        this.serializerId = serializerSegmentId;
        this.serializedRepresentation = serializedRepresentation;
    }

    @Override
    public void onEvent(CommandHandlingEntry event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getSerializerSegmentId() == serializerId) {
            for (EventMessage eventMessage : event.getUnitOfWork().getEventsToPublish()) {
                if (eventMessage instanceof SerializationAware) {
                    ((SerializationAware) eventMessage).serializePayload(serializer, serializedRepresentation);
                    ((SerializationAware) eventMessage).serializeMetaData(serializer, serializedRepresentation);
                }
            }
        }
    }
}
