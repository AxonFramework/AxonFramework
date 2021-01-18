/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.modelling.command.GenericJpaRepository

/**
 * Reified version of the static builder for event souring repository.
 * @param T aggregate type.
 * @return event sourcing repository builder for aggregate [T]
 * @since 0.2.0
 */
inline fun <reified T : Any> eventSourcingRepositoryBuilder() = EventSourcingRepository.builder(T::class.java)


/**
 * Reified version of the static builder for JPA repository.
 * @param T aggregate type.
 * @return Generic JPA repository builder for aggregate [T]
 * @since 0.2.0
 */
inline fun <reified T : Any> genericJpaRepositoryBuilder() = GenericJpaRepository.builder(T::class.java)