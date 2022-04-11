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

internal data class UpdateType(val dummy:String)

internal class ExampleSubscriptionQueryResult:SubscriptionQueryResult<String, UpdateType> {
    override fun cancel(): Boolean {
        TODO("Not yet implemented")
    }

    override fun initialResult(): Mono<String> {
        TODO("Not yet implemented")
    }

    override fun updates(): Flux<UpdateType> {
        TODO("Not yet implemented")
    }


}
