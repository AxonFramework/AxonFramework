package org.axonframework.extensions.kotlin

import org.axonframework.queryhandling.QueryUpdateEmitter

/**
 * Reified version of [org.axonframework.queryhandling.QueryUpdateEmitter.emit] which uses generics
 * to indicate Query type and Update type.
 *
 * Emits given incremental update to subscription queries matching given generic query type and filter.
 * In order to send nullable updates, use [org.axonframework.queryhandling.QueryUpdateEmitter.emit]
 * with an [org.axonframework.queryhandling.SubscriptionQueryUpdateMessage]
 *
 * @param update    incremental update
 * @param filter    predicate on query payload used to filter subscription queries
 * @param [Q]       the type of the query
 * @param [U]       the type of the update
 * @see org.axonframework.queryhandling.QueryUpdateEmitter.emit
 * @author Stefan Andjelkovic
 */
inline fun <reified Q, reified U : Any> QueryUpdateEmitter.emit(update: U, noinline filter: (Q) -> Boolean) =
        this.emit(Q::class.java, filter, update)


