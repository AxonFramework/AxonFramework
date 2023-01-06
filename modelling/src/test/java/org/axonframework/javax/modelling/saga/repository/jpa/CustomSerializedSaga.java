/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.javax.modelling.saga.repository.jpa;

import org.axonframework.serialization.SimpleSerializedObject;

/**
 * Specialization of the SerializedObject for Sagas represented as String array.
 *
 * @author Christophe Bouhier
 * @since 2.0
 */
public class CustomSerializedSaga extends SimpleSerializedObject<String> {

    /**
     * Initialize a SerializedSaga instance with given {@code data}, of given {@code type} and
     * {@code revision}.
     *
     * @param data     The binary data of the Saga
     * @param type     The type of saga
     * @param revision The revision of the serialized version
     */
    public CustomSerializedSaga(String data, String type, String revision) {
        super(data, String.class, type, revision);
    }
}