/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging;

import org.axonframework.common.AxonException;

import java.util.List;

/**
 * Exception indicating that an error has occurred while remotely handling a message.
 * <p/>
 * By default, a stack trace is not generated for this exception. However, the stack trace creation can be enforced
 * explicitly via the constructor accepting the {@code writableStackTrace} parameter.
 * <p/>
 * The sender of the message <strong>cannot</strong> assume that the message has not been handled. It may, if the type
 * of message or the infrastructure allows it, try to dispatch the message again.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class RemoteHandlingException extends AxonException {

    private static final long serialVersionUID = 7310513417002285205L;

    private final List<String> exceptionDescriptions;

    /**
     * Initializes the exception using the given {@code exceptionDescription} describing the remote cause-chain.
     *
     * @param exceptionDescription a {@link String} describing the remote exceptions
     */
    public RemoteHandlingException(RemoteExceptionDescription exceptionDescription) {
        this(exceptionDescription, false);
    }

    /**
     * Initializes the exception using the given {@code exceptionDescription} and {@code writableStackTrace}.
     *
     * @param exceptionDescription a {@link String} describing the remote exceptions
     * @param writableStackTrace   whether the stack trace should be generated ({@code true}) or not ({@code false})
     */
    public RemoteHandlingException(RemoteExceptionDescription exceptionDescription, boolean writableStackTrace) {
        super("An exception was thrown by the remote message handling component: " + exceptionDescription.toString(),
              null, writableStackTrace);
        this.exceptionDescriptions = exceptionDescription.getDescriptions();
    }

    /**
     * Returns a {@link List} of {@link String}s describing the remote exception.
     *
     * @return a {@link List} of {@link String}s describing the remote exception
     */
    public List<String> getExceptionDescriptions() {
        return exceptionDescriptions;
    }
}