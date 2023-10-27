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

import org.axonframework.serialization.upcasting.Upcaster;

/**
 * Interface that is used for upcasters of event data. The event data provides event context like event identifier,
 * aggregate identifier, event metadata etc.
 *
 * @author Rene de Waele
 * @since 3.0
 */
@FunctionalInterface
public interface EventUpcaster extends Upcaster<IntermediateEventRepresentation> {

}
