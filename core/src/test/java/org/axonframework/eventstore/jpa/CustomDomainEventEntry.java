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

package org.axonframework.eventstore.jpa;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedMetaData;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;


import javax.persistence.Entity;
import javax.persistence.Lob;
import java.time.Instant;

/**
 * @author Allard Buijze
 */
@Entity
public class CustomDomainEventEntry extends AbstractEventEntryData<String> {

    @Lob
    private String metaData;

    @Lob
    private String payload;

    public CustomDomainEventEntry(DomainEventMessage event,
                                  Instant timestamp,
                                  SerializedObject<String> payload,
                                  SerializedObject<String> metaData) {
        super(event.getIdentifier(),
              event.getAggregateIdentifier(),
              event.getSequenceNumber(),
              timestamp, payload.getType()
        );
        this.payload = payload.getData();
        this.metaData = metaData.getData();
    }

    public CustomDomainEventEntry() {
    }

    @Override
    public SerializedObject<String> getMetaData() {
        return new SerializedMetaData<>(metaData, String.class);
    }

    @Override
    public SerializedObject<String> getPayload() {
        return new SimpleSerializedObject<>(payload, String.class, getPayloadType());
    }
}
