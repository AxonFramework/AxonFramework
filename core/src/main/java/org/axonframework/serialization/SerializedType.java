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

package org.axonframework.serialization;

/**
 * Describes the type of a serialized object. This information is used to decide how to deserialize an object.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface SerializedType {

    /**
     * Returns the name of the serialized type. This may be the class name of the serialized object, or an alias for
     * that name.
     *
     * @return the name of the serialized type
     */
    String getName();

    /**
     * Returns the revision identifier of the serialized object. This revision identifier is used by upcasters to
     * decide how to transform serialized objects during deserialization.
     *
     * @return the revision identifier of the serialized object
     */
    String getRevision();
}
