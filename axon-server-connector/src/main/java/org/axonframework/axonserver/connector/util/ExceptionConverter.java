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

package org.axonframework.axonserver.connector.util;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.SerializedObject;
import jakarta.annotation.Nullable;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.common.AxonException;

import java.util.Optional;
import java.util.function.Predicate;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Utility class used to serializer {@link Throwable}s into {@link ErrorMessage}s.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public final class ExceptionConverter {

    /**
     * Converts the given error information into an {@link AxonException}.
     *
     * @param errorCode     The error code identifying the type of exception to convert to.
     * @param errorMessage  The {@link ErrorMessage} containing details of the error.
     * @param payload       An optional {@link SerializedObject} representing additional error data, which may
     *                      be used in the exception conversion.
     * @return An instance of {@link AxonException} representing the given error information.
     */
    public static AxonException convertToAxonException(String errorCode, ErrorMessage errorMessage, SerializedObject payload) {
        return ErrorCode.getFromCode(errorCode)
                        .convert(errorMessage,
                                 () -> Optional.ofNullable(payload).map(SerializedObject::getData)
                                         .filter(Predicate.not(ByteString::isEmpty))
                                         .map(ByteString::toByteArray)
                                         .orElse(null)
                        );
    }

    /**
     * Serializes a given {@link Throwable} into an {@link ErrorMessage}.
     *
     * @param clientLocation the name of the client were the {@link ErrorMessage} originates from
     * @param errorCode The error code identifying the type of action that resulted in an error, if known
     * @param t              the {@link Throwable} to base this {@link ErrorMessage} on
     * @return the {@link ErrorMessage} originating from the given {@code clientLocation} and based on the
     * {@link Throwable}
     */
    public static ErrorMessage convertToErrorMessage(String clientLocation, @Nullable ErrorCode errorCode,
                                                     Throwable t) {
        ErrorMessage.Builder builder =
                ErrorMessage.newBuilder()
                            .setLocation(getOrDefault(clientLocation, ""))
                            .setMessage(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        if (errorCode != null) {
            builder.setErrorCode(errorCode.errorCode());
        }
        builder.addDetails(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        while (t.getCause() != null) {
            t = t.getCause();
            builder.addDetails(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        }
        return builder.build();
    }

    private ExceptionConverter() {
        // Utility class
    }
}
