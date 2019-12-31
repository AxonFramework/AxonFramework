/*-
 * #%L
 * Axon Framework Kotlin Extension
 * %%
 * Copyright (C) 2019 AxonIQ
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * #L%
 */
package org.axonframework.extensions.kotlin

import org.axonframework.serialization.SimpleSerializedType
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation
import org.axonframework.serialization.upcasting.event.SingleEventUpcaster
import kotlin.reflect.KClass

/**
 * Helpers for event upcaster.
 */
object EventUpcaster {
    /**
     * Creates a singleEventUpcaster for given type and revisions and calls [IntermediateEventRepresentation.upcastPayload] using the [converter].
     */
    fun <T : Any> singleEventUpcaster(eventType: KClass<*>,
                                      storageType: KClass<T>,
                                      revisions: Revisions,
                                      converter: (T) -> T): SingleEventUpcaster = object : SingleEventUpcaster() {

        override fun canUpcast(ir: IntermediateEventRepresentation): Boolean = SimpleSerializedType(eventType.qualifiedName, revisions.first) == ir.type

        override fun doUpcast(ir: IntermediateEventRepresentation): IntermediateEventRepresentation =
                ir.upcastPayload(
                        SimpleSerializedType(eventType.qualifiedName, revisions.second),
                        storageType.java,
                        converter)
    }
}

/**
 * Tuple of oldRevision (nullable) and newRevision.
 */
typealias Revisions = Pair<String?, String>
