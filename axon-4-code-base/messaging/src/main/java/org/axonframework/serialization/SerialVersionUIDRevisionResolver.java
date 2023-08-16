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

package org.axonframework.serialization;

import java.io.ObjectStreamClass;

/**
 * RevisionResolver implementation that returns the (String representation of the) serialVersionUID of a class. If a
 * class is not serializable, it returns {@code null} when asked for a revision.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerialVersionUIDRevisionResolver implements RevisionResolver {

    @Override
    public String revisionOf(Class<?> payloadType) {
        ObjectStreamClass objectStreamClass = ObjectStreamClass.lookup(payloadType);
        return objectStreamClass == null ? null : Long.toString(objectStreamClass.getSerialVersionUID());
    }
}
