/*
 * Copyright (c) 2010-2026. Axon Framework
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
package org.axonframework.extension.kotlin.eventsourcing

import org.axonframework.extension.kotlin.messaging.FunctionalCommandHandlerComponent
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule.CommandHandlerPhase
import kotlin.reflect.KFunction

fun CommandHandlerPhase.functionalHandler(
    function: KFunction<*>,
    instance: Any? = null
) = this.commandHandlingComponent { configuration ->
    FunctionalCommandHandlerComponent(
        function = function,
        configuration = configuration,
        instance = instance
    )
}
