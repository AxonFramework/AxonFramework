package org.axonframework.extensions.kotlin

import org.axonframework.modelling.command.TargetAggregateIdentifier

/**
 * Simple Query class to be used in tests.
 */
internal data class ExampleQuery(val value: Number)

/**
 * Simple Command class to be used in tests.
 */
internal data class ExampleCommand(@TargetAggregateIdentifier val id: String)
