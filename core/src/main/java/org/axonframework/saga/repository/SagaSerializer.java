/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository;

import org.axonframework.saga.Saga;

/**
 * Interface towards a serialization mechanism for Saga instances.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface SagaSerializer {

    /**
     * Serialize the given <code>saga</code> to a byte array.
     *
     * @param saga The saga to serialize
     * @return The bytes representing the serialized form of the Saga
     *
     * @throws org.axonframework.util.SerializationException
     *          if a technical error occurs while deserializing the Saga
     */
    byte[] serialize(Saga saga);

    /**
     * Deserializes the given <code>serializedSaga</code>.
     *
     * @param serializedSaga the bytes representing the serialized form of a Saga
     * @return The Saga instance represented by the given bytes
     *
     * @throws ClassCastException if the given bytes do not represent a Saga
     * @throws org.axonframework.util.SerializationException
     *                            if a technical error occurs while deserializing the Saga
     */
    Saga deserialize(byte[] serializedSaga);
}
