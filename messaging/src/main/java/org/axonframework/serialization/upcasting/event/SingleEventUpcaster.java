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


import org.axonframework.serialization.upcasting.SingleEntryUpcaster;
import org.axonframework.serialization.upcasting.Upcaster;

/**
 * Abstract implementation of an event {@link Upcaster} that eases the common process of upcasting one intermediate
 * event representation to another representation by applying a simple mapping function to the input stream of
 * intermediate representations.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class SingleEventUpcaster
        extends SingleEntryUpcaster<IntermediateEventRepresentation> implements EventUpcaster {

}
