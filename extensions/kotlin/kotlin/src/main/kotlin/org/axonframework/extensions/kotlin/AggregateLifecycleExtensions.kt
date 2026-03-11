/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.extensions.kotlin

import org.axonframework.messaging.MetaData
import org.axonframework.modelling.command.Aggregate
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.ApplyMore

/**
 * Alias for [AggregateLifecycle.apply] method.
 * @param payload payload of the event message to be applied.
 * @return fluent instance to apply more.
 * @since 0.2.0
 */
fun applyEvent(payload: Any): ApplyMore = AggregateLifecycle.apply(payload)

/**
 * Alias for [AggregateLifecycle.apply] method.
 * @param payload payload of the event message to be applied.
 * @param metaData metadata to be included into the event message.
 * @return fluent instance to apply more.
 * @since 0.2.0
 */
fun applyEvent(payload: Any, metaData: MetaData): ApplyMore = AggregateLifecycle.apply(payload, metaData)

/**
 * Create new aggregate instance.
 * @param T aggregate type.
 * @param factoryMethod factory method.
 * @return new instance of an aggregate.
 * @since 0.2.0
 */
inline fun <reified T : Any> createNew(noinline factoryMethod: () -> T): Aggregate<T> = AggregateLifecycle.createNew(T::class.java, factoryMethod)
