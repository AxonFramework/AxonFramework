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

/**
 * Interface towards a mechanism that resolves the revision of a given payload type. Based on this revision, a
 * component is able to recognize whether a serialized version of the payload is compatible with the
 * currently known version of the payload.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RevisionResolver {

    /**
     * Returns the revision for the given {@code payloadType}.
     * <p/>
     * The revision is used by upcasters to decide whether they need to process a certain serialized event.
     * Generally, the revision needs to be modified each time the structure of an event has been changed in an
     * incompatible manner.
     *
     * @param payloadType The type for which to return the revision
     * @return the revision for the given {@code payloadType}
     */
    String revisionOf(Class<?> payloadType);
}
