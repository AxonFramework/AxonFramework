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
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;

/**
 * @author Rene de Waele
 */
public class SerializedToken<T> {
    private final T data;
    private final String dataType;

    public SerializedToken(T data, String dataType) {
        this.data = data;
        this.dataType = dataType;
    }

    @SuppressWarnings("unchecked")
    public TrackingToken getToken(Serializer serializer) {
        SimpleSerializedObject<T> serializedObject = new SimpleSerializedObject<T>(data, (Class<T>) data.getClass(),
                                                                                   new SimpleSerializedType(dataType,
                                                                                                            null));
        return serializer.deserialize(serializedObject);
    }
}
