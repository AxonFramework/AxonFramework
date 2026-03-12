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

package org.axonframework.extensions.kotlin

import org.axonframework.messaging.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.commandhandling.gateway.CommandResult
import java.util.concurrent.CompletableFuture

/**
 * Sends the given [command] and returns the [CommandResult] for async handling.
 *
 * @param command The command to send
 * @param C the type of the command
 * @return [CommandResult] allowing callbacks via [CommandResult.onSuccess] / [CommandResult.onError]
 * @see CommandGateway.send
 * @since 0.5.0
 */
inline fun <reified C : Any> CommandGateway.send(command: C): CommandResult =
    // Cast to Any to resolve the call to CommandGateway.send(Object) and avoid infinite recursion
    // (without the cast, the Kotlin compiler would re-dispatch to this inline extension).
    this.send(command as Any)

/**
 * Sends the given [command] and returns a [CompletableFuture] with the result typed as [R].
 *
 * @param command The command to send
 * @param C the type of the command
 * @param R the expected result type
 * @return [CompletableFuture] of [R]
 * @see CommandGateway.send
 * @since 0.5.0
 */
inline fun <reified C : Any, reified R : Any> CommandGateway.sendForResult(command: C): CompletableFuture<R> =
    this.send(command as Any, R::class.java)

/**
 * Sends the given [command] and blocks until a result typed as [R] is available.
 *
 * This extension resolves the JVM overload ambiguity: at the call site `gateway.sendAndWait<MyCmd, MyResult>(cmd)`
 * binds to [CommandGateway.sendAndWait] `(Object, Class<R>)`, not to the single-argument `(Object)` overload.
 *
 * @param command The command to send
 * @param C the type of the command
 * @param R the expected result type
 * @return the result of type [R]
 * @see CommandGateway.sendAndWait
 * @since 0.5.0
 */
inline fun <reified C : Any, reified R : Any> CommandGateway.sendAndWait(command: C): R =
    // Cast to Any to route to CommandGateway.sendAndWait(Object, Class<R>) and avoid recursion.
    // The Java method is @Nullable; fail fast with a clear message rather than propagating null silently.
    this.sendAndWait(command as Any, R::class.java)
        ?: error("sendAndWait returned null; if a null result is expected, use sendForResult instead")
