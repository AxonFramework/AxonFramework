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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.serialization.upcasting.GenericUpcasterChain;

import java.util.List;

/**
 * Upcaster chain used to upcast {@link IntermediateEventRepresentation event representations}.
 * <p/>
 * Upcasters expecting different serialized object types may be merged into a single chain, as long as the order of
 * related upcasters can be guaranteed.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class EventUpcasterChain extends GenericUpcasterChain<IntermediateEventRepresentation> implements EventUpcaster {

    /**
     * Initializes an upcaster chain from one or more upcasters.
     *
     * @param upcasters the upcasters to chain
     */
    public EventUpcasterChain(EventUpcaster... upcasters) {
        super(upcasters);
    }

    /**
     * Initializes an upcaster chain from the given list of upcasters.
     *
     * @param upcasters the upcasters to chain
     */
    public EventUpcasterChain(List<? extends EventUpcaster> upcasters) {
        super(upcasters);
    }
}
