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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.commandhandling.GenericCommandResultMessage
import org.axonframework.commandhandling.distributed.CommandDispatchException
import org.axonframework.messaging.MetaData
import org.junit.Test
import java.util.*
import kotlin.test.fail

class CombiningCommandCallbackTest {
    private val command = ExampleCommand("1")
    private val commandMessage: CommandMessage<ExampleCommand> = GenericCommandMessage.asCommandMessage(command)
    private val responsePayloadUUID = UUID.randomUUID().toString()
    private val commandResultMessage = GenericCommandResultMessage.asCommandResultMessage<String>(responsePayloadUUID)
    private val exceptionalCommandResultMessage = GenericCommandResultMessage.asCommandResultMessage<String>(CommandDispatchException("Exception message"))
    private val metaData = MetaData.with("key", "value")

    @Test
    fun `Should invoke onResult for successful response`() {
        val onSuccessMock = mockk<(commandMessage: CommandMessage<out ExampleCommand>, result: String, metaData: MetaData) -> Unit>()
        every { onSuccessMock.invoke(commandMessage, responsePayloadUUID, any()) } returns Unit

        val subject = CombiningCommandCallback<ExampleCommand, String>(
            onError = { _, _, _ -> fail("onError should not be called") },
            onSuccess = onSuccessMock
        )

        subject.onResult(commandMessage, commandResultMessage.withMetaData(metaData))

        verify { subject.onSuccess.invoke(commandMessage, responsePayloadUUID, metaData) }
    }

    @Test
    fun `Should invoke onResult for successful response and provide default metadata`() {
        val onSuccessMock = mockk<(commandMessage: CommandMessage<out ExampleCommand>, result: String, metaData: MetaData) -> Unit>()
        every { onSuccessMock.invoke(commandMessage, responsePayloadUUID, any()) } returns Unit

        val subject = CombiningCommandCallback(
            onError = { _, _, _ -> fail("onError should not be called") },
            onSuccess = onSuccessMock
        )

        subject.onResult(commandMessage, commandResultMessage)

        verify { subject.onSuccess.invoke(commandMessage, responsePayloadUUID, MetaData.emptyInstance()) }
    }

    @Test
    fun `Should invoke onResult for exceptional response`() {
        val onError = mockk<(commandMessage: CommandMessage<out ExampleCommand>, exception: Throwable, metaData: MetaData) -> Unit>()
        every { onError.invoke(commandMessage, exceptionalCommandResultMessage.exceptionResult(), any()) } returns Unit

        val subject = CombiningCommandCallback<ExampleCommand, String>(
            onError = onError,
            onSuccess = { _, _, _ -> fail("onSuccess should not be called") }
        )

        subject.onResult(commandMessage, exceptionalCommandResultMessage.withMetaData(metaData))

        verify { subject.onError.invoke(commandMessage, exceptionalCommandResultMessage.exceptionResult(), metaData) }
    }


    @Test
    fun `Should invoke onResult for exceptional response and provide default metadata`() {
        val onError = mockk<(commandMessage: CommandMessage<out ExampleCommand>, exception: Throwable, metaData: MetaData) -> Unit>()
        every { onError.invoke(commandMessage, exceptionalCommandResultMessage.exceptionResult(), any()) } returns Unit

        val subject = CombiningCommandCallback<ExampleCommand, String>(
            onError = onError,
            onSuccess = { _, _, _ -> fail("onSuccess should not be called") }
        )

        subject.onResult(commandMessage, exceptionalCommandResultMessage)

        verify { subject.onError.invoke(commandMessage, exceptionalCommandResultMessage.exceptionResult(), MetaData.emptyInstance()) }
    }
}
