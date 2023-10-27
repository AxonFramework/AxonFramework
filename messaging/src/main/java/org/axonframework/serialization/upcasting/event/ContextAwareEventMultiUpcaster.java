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

import org.axonframework.serialization.upcasting.ContextAwareSingleEntryMultiUpcaster;
import org.axonframework.serialization.upcasting.SingleEntryMultiUpcaster;

/**
 * Abstract implementation of a {@link SingleEntryMultiUpcaster} and an {@link EventUpcaster} that eases the common
 * process of upcasting one intermediate event representation to several other representations by applying a flat
 * mapping function to the input stream of intermediate representations. Additionally, it's a context aware
 * implementation, which enables it to store and reuse context information from one entry to another during upcasting.
 *
 * @param <C> the type of context used as {@code C}
 * @author Steven van Beelen
 * @since 3.1
 */
public abstract class ContextAwareEventMultiUpcaster<C>
        extends ContextAwareSingleEntryMultiUpcaster<IntermediateEventRepresentation, C> implements EventUpcaster {

}
