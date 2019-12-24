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

import org.axonframework.commandhandling.CommandCallback
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.MetaData
import java.util.concurrent.TimeUnit

/**
 * Callback-style send with dedicated on-success and on-error functions (defaults do nothing)
 */
inline fun <reified C : Any, reified R : Any?> CommandGateway.send(
    command: C,
    crossinline onSuccess: (commandMessage: CommandMessage<out C>, result: R, metaData: MetaData) -> Unit = { _, _, _ -> },
    crossinline onError: (commandMessage: CommandMessage<out C>, exception: Throwable, metaData: MetaData) -> Unit = { _, _, _ -> }
) {
    this.send(command, CommandCallback<C, R> { commandMessage, callBack ->
        val metaData = callBack.metaData ?: MetaData.emptyInstance()
        if (callBack.isExceptional) {
            onError(commandMessage, callBack.exceptionResult(), metaData)
        } else {
            onSuccess(commandMessage, callBack.payload, metaData)
        }
    })
}

/**
 * Reified version of send and wait.
 */
inline fun <reified R : Any?> CommandGateway.sendAndWaitWithResponse(command: Any) =
    this.sendAndWait<R>(command)

/**
 * Reified version of send and wait with a timeout (defaulting to {@link TimeUnit.MILLISECONDS} unit)
 */
inline fun <reified R : Any?> CommandGateway.sendAndWaitWithResponse(command: Any, timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) =
    this.sendAndWait<R>(command, timeout, unit)
