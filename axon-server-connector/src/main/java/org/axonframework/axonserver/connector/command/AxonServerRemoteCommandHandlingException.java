/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.messaging.RemoteExceptionDescription;
import org.axonframework.messaging.RemoteHandlingException;

/**
 * Exception indicating a problem that was reported by the remote end of a connection.
 * <p/>
 * By default, a stack trace is not generated for this exception. However, the stack trace creation can be enforced
 * explicitly via the constructor accepting the {@code writableStackTrace} parameter.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerRemoteCommandHandlingException extends RemoteHandlingException {

    private final String errorCode;
    private final String server;

    /**
     * Initialize the exception with given {@code errorCode} and {@code errorMessage}.
     *
     * @param errorCode    the code reported by the server
     * @param errorMessage the message describing the exception on the remote end
     */
    public AxonServerRemoteCommandHandlingException(String errorCode, ErrorMessage errorMessage) {
        this(errorCode, errorMessage, false);
    }

    /**
     * Initialize the exception with given {@code errorCode}, {@code errorMessage} and {@code writableStackTrace}.
     *
     * @param errorCode          the code reported by the server
     * @param errorMessage       the message describing the exception on the remote end
     * @param writableStackTrace whether the stack trace should be generated ({@code true}) or not ({@code false})
     */
    public AxonServerRemoteCommandHandlingException(String errorCode, ErrorMessage errorMessage,
                                                    boolean writableStackTrace) {
        super(new RemoteExceptionDescription(errorMessage.getDetailsList()), writableStackTrace);
        this.errorCode = errorCode;
        this.server = errorMessage.getLocation();
    }

    /**
     * Returns the name of the server that reported the error.
     *
     * @return the name of the server that reported the error
     */
    public String getOriginServer() {
        return server;
    }

    /**
     * Returns the error code as reported by the server.
     *
     * @return the error code as reported by the server
     */
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "AxonServerRemoteCommandHandlingException{" +
                "message=" + getMessage() +
                ", errorCode='" + errorCode + '\'' +
                ", server='" + server + '\'' +
                '}';
    }
}