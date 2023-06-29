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

package org.axonframework.extension.kotlin.test

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.RoutingKey
import org.axonframework.modelling.command.AggregateIdentifier

internal data class ExampleCommand(@RoutingKey val aggregateId: String)
internal data class ExampleCommandWithException(@RoutingKey val aggregateId: String)

internal class ExampleAggregate {

    @AggregateIdentifier
    lateinit var aggregateId: String

    constructor()

    @CommandHandler
    constructor(command: ExampleCommand)

    @CommandHandler
    constructor(command: ExampleCommandWithException) {
        throw Exception()
    }
}

class ExampleSaga