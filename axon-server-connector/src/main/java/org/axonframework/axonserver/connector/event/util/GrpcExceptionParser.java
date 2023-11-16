/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector.event.util;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.axonframework.axonserver.connector.ErrorCode;

/**
 * Converts GRPC Exceptions to {@link RuntimeException}s.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class GrpcExceptionParser {

    private static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("AxonIQ-ErrorCode", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Convert the give {@code exception} into a {@link RuntimeException}.
     *
     * @param exception the {@link Throwable} to base the {@link RuntimeException}.
     * @return the {@link RuntimeException} based on the given {@code exception}
     */
    public static RuntimeException parse(Throwable exception) {
        String code = "AXONIQ-0001";
        if (exception instanceof StatusRuntimeException statusRuntimeException) {
            if (Status.Code.UNIMPLEMENTED.equals(statusRuntimeException.getStatus().getCode())) {
                return new UnsupportedOperationException(exception.getMessage(), exception);
            }
            Metadata trailer = statusRuntimeException.getTrailers();
            String errorCode = trailer == null ? null : trailer.get(ERROR_CODE_KEY);
            if (errorCode != null) {
                code = errorCode;
            }
        }
        return ErrorCode.getFromCode(code).convert(exception);
    }
}
