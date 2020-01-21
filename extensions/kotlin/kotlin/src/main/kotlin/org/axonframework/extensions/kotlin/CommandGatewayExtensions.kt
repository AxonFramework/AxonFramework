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

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.MetaData
import java.util.concurrent.TimeUnit

/**
 * Callback-style [CommandGateway.send] with dedicated on-success and on-error functions
 * @param command The command to send
 * @param onError Callback to handle failed execution
 * @param onSuccess Callback to handle successful execution
 * @param [R] the type of result of the command handling
 * @param [C] the type of payload of the command
 * @see CommandGateway.send
 */
fun <C : Any, R : Any?> CommandGateway.send(
    command: C,
    onSuccess: (commandMessage: CommandMessage<out C>, result: R, metaData: MetaData) -> Unit = { _, _, _ -> },
    onError: (commandMessage: CommandMessage<out C>, exception: Throwable, metaData: MetaData) -> Unit = { _, _, _ -> }
): Unit = this.send(command, ResultDiscriminatorCommandCallback<C, R>(onSuccess, onError))

/**
 * Reified version of [CommandGateway.sendAndWait]
 * @param command The command to send
 * @param [R] The expected type of return value
 * @return The result of the command handler execution
 * @throws org.axonframework.commandhandling.CommandExecutionException when command execution threw a checked exception
 */
inline fun <reified R : Any?> CommandGateway.sendAndWaitWithResponse(command: Any): R =
    this.sendAndWait<R>(command)

/**
 * Reified version of [CommandGateway.sendAndWait] with a timeout (defaulting to [TimeUnit.MILLISECONDS] unit)
 * @param command The command to send
 * @param timeout The maximum time to wait
 * @param unit The time unit of the timeout argument. Defaults to [TimeUnit.MILLISECONDS]
 * @param [R] The expected type of return value
 * @return The result of the command handler execution
 * @throws org.axonframework.commandhandling.CommandExecutionException when command execution threw a checked exception
 */
inline fun <reified R : Any?> CommandGateway.sendAndWaitWithResponse(command: Any, timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): R =
    this.sendAndWait<R>(command, timeout, unit)
