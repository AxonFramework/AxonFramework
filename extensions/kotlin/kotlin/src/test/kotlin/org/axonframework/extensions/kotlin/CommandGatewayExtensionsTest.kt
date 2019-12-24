package org.axonframework.extensions.kotlin

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.axonframework.commandhandling.CommandCallback
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.MetaData
import org.axonframework.modelling.command.AggregateIdentifier
import org.junit.Test
import java.util.concurrent.TimeUnit

class CommandGatewayExtensionsTest {
    private val subjectGateway = mockk<CommandGateway>()

    private val exampleCommand = ExampleCommand("1")
    private val timeoutInterval: Long = 30
    private val defaultTimeUnit = TimeUnit.MILLISECONDS

    @Test
    fun `Send extension should invoke correct method on the gateway`() {
        every { subjectGateway.send(exampleCommand, any<CommandCallback<ExampleCommand, Any>>()) } just Runs

        subjectGateway.send<ExampleCommand, Any>(command = exampleCommand, onError = { a, b, c -> }, onSuccess = { a, b, c -> })

        verify { subjectGateway.send(exampleCommand, any<CommandCallback<ExampleCommand, Any>>()) }
    }

    @Test
    fun `Send extension should invoke correct method on the gateway without explicit generic parameters`() {
        every { subjectGateway.send(exampleCommand, any<CommandCallback<ExampleCommand, Any>>()) } just Runs

        subjectGateway.send(
            command = exampleCommand,
            onError = { a: Any, b: Throwable, c: MetaData -> },
            onSuccess = { a: CommandMessage<out ExampleCommand>, b: Any, c: MetaData -> }
        )

        verify { subjectGateway.send(exampleCommand, any<CommandCallback<ExampleCommand, Any>>()) }
    }

    @Test
    fun `SendAndWaitWithResponse extension should invoke correct method on the gateway`() {
        every { subjectGateway.sendAndWait<Any>(exampleCommand) } returns " "

        subjectGateway.sendAndWaitWithResponse<Any>(exampleCommand)

        verify { subjectGateway.sendAndWait<Any>(exampleCommand) }
    }

    @Test
    fun `SendAndWaitWithResponse extension should invoke correct method on the gateway without explicit generic parameter`() {
        every { subjectGateway.sendAndWait<Any>(exampleCommand) } returns " "

        fun methodWithExplicitReturnValue(): Unit = subjectGateway.sendAndWaitWithResponse(exampleCommand)

        methodWithExplicitReturnValue()

        verify { subjectGateway.sendAndWait<Any>(exampleCommand) }
    }

    @Test
    fun `SendAndWaitWithResponse with timeout extension should invoke correct method on the gateway`() {
        every { subjectGateway.sendAndWait<Any>(exampleCommand, timeoutInterval, TimeUnit.MICROSECONDS) } returns Unit

        subjectGateway.sendAndWaitWithResponse<Any>(command = exampleCommand, timeout = timeoutInterval, unit = TimeUnit.MICROSECONDS)

        verify { subjectGateway.sendAndWait<Any>(exampleCommand, timeoutInterval, TimeUnit.MICROSECONDS) }
    }

    @Test
    fun `SendAndWaitWithResponse with timeout extension should invoke correct method on the gateway without explicit generic parameter`() {
        every { subjectGateway.sendAndWait<Any>(exampleCommand, timeoutInterval, TimeUnit.MICROSECONDS) } returns Unit

        fun methodWithExplicitReturnValue(): Unit =
            subjectGateway.sendAndWaitWithResponse(
                command = exampleCommand,
                timeout = timeoutInterval,
                unit = TimeUnit.MICROSECONDS
            )

        methodWithExplicitReturnValue()

        verify { subjectGateway.sendAndWait<Any>(exampleCommand, timeoutInterval, TimeUnit.MICROSECONDS) }
    }

    @Test
    fun `SendAndWaitWithResponse with timeout extension should invoke correct method on the gateway with default Timeunit`() {
        every { subjectGateway.sendAndWait<Any>(exampleCommand, timeoutInterval, defaultTimeUnit) } returns Unit

        subjectGateway.sendAndWaitWithResponse<Any>(command = exampleCommand, timeout = timeoutInterval)

        verify { subjectGateway.sendAndWait<Any>(exampleCommand, timeoutInterval, defaultTimeUnit) }
    }
}

data class ExampleCommand(@AggregateIdentifier val id: String)
