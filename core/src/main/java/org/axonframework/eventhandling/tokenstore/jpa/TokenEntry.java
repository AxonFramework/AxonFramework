/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;

import javax.persistence.Basic;
import javax.persistence.Entity;
import java.time.Instant;

/**
 * @author Rene de Waele
 */
@Entity
public class TokenEntry extends AbstractTokenEntry<byte[]> {

    @Basic(optional = false)
    private String timestamp;

    public TokenEntry(String process, int segment, TrackingToken token, Instant timestamp, Serializer serializer) {
        super(process, segment, token, serializer, byte[].class);
        this.timestamp = timestamp.toString();
    }
    protected TokenEntry() {
    }

    public String timestamp() {
        return timestamp;
    }
}
