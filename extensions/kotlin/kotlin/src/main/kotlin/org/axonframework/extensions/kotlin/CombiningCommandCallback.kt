/*-
 * #%L
 * Axon Framework - Kotlin Extension
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
import org.axonframework.commandhandling.CommandResultMessage
import org.axonframework.messaging.MetaData

/**
 * Implementation of the [CommandCallback] that is appropriate for dedicated [onError] and [onSuccess] callbacks
 */
internal class CombiningCommandCallback<C, R>(
    val onError: (commandMessage: CommandMessage<out C>, exception: Throwable, metaData: MetaData) -> Unit,
    val onSuccess: (commandMessage: CommandMessage<out C>, result: R, metaData: MetaData) -> Unit
) : CommandCallback<C, R> {
    override fun onResult(commandMessage: CommandMessage<out C>, commandResultMessage: CommandResultMessage<out R>) {
        val metaData = commandResultMessage.metaData ?: MetaData.emptyInstance()
        if (commandResultMessage.isExceptional) {
            onError(commandMessage, commandResultMessage.exceptionResult(), metaData)
        } else {
            onSuccess(commandMessage, commandResultMessage.payload, metaData)
        }
    }
}

