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

package org.axonframework.upcasting;

/**
 * Interface indicating that the implementing mechanism is aware of Upcasters. Upcasting is the process where
 * deprecated Domain Objects (typically Events) are converted into the current format. This process typically occurs
 * with Domain Events that have been stored in an Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface UpcasterAware {

    /**
     * Sets the UpcasterChain which allow older revisions of serialized objects to be deserialized.
     *
     * @param upcasterChain the upcaster chain providing the upcasting capabilities
     */
    void setUpcasterChain(UpcasterChain upcasterChain);
}
