package org.axonframework.extensions.kotlin

import org.axonframework.commandhandling.CommandCallback
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.CommandResultMessage
import org.axonframework.messaging.MetaData

/**
 * Implementation of the [CommandCallback] that is appropriate for dedicated onError and onSuccess callbacks
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

