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

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.MetaData
import java.util.concurrent.TimeUnit

/**
 * Callback-style send with dedicated on-success and on-error functions (defaults do nothing)
 */
fun <C : Any, R : Any?> CommandGateway.send(
    command: C,
    onSuccess: (commandMessage: CommandMessage<out C>, result: R, metaData: MetaData) -> Unit = { _, _, _ -> },
    onError: (commandMessage: CommandMessage<out C>, exception: Throwable, metaData: MetaData) -> Unit = { _, _, _ -> }
): Unit = this.send(command, CombiningCommandCallback<C, R>(onError, onSuccess))

/**
 * Reified version of [CommandGateway.sendAndWait].
 */
inline fun <reified R : Any?> CommandGateway.sendAndWaitWithResponse(command: Any): R =
    this.sendAndWait<R>(command)

/**
 * Reified version of [CommandGateway.sendAndWait] with a timeout (defaulting to [TimeUnit.MILLISECONDS] unit)
 */
inline fun <reified R : Any?> CommandGateway.sendAndWaitWithResponse(command: Any, timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): R =
    this.sendAndWait<R>(command, timeout, unit)
