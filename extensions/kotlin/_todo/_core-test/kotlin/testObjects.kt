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

package org.axonframework.extensions.kotlin

import org.axonframework.modelling.command.TargetAggregateIdentifier
import org.axonframework.queryhandling.SubscriptionQueryResult
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Simple Query class to be used in tests.
 */
internal data class ExampleQuery(val value: Number)

/**
 * Simple Command class to be used in tests.
 */
internal data class ExampleCommand(@TargetAggregateIdentifier val id: String)

/**
 * Class used as update response type in subscriptionQuery method.
 */
internal data class UpdateResponseType(val dummy: String)

/**
 * Class used as initial response type in subscriptionQuery method.
 */
internal data class InitialResponseType(val dummy: String)

/**
 * Dummy class used as return object from subscriptionQuery method in the mock.
 */
internal class ExampleSubscriptionQueryResult : SubscriptionQueryResult<InitialResponseType, UpdateResponseType> {
    override fun cancel(): Boolean {
        TODO("Not yet implemented")
    }

    override fun initialResult(): Mono<InitialResponseType> {
        TODO("Not yet implemented")
    }

    override fun updates(): Flux<UpdateResponseType> {
        TODO("Not yet implemented")
    }
}
