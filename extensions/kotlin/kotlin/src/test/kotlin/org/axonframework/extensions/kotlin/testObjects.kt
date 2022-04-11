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
