/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector.event.util;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.axonframework.axonserver.connector.ErrorCode;

/**
 * Converts GRPC Exceptions to EventStoreClientException
 */
public class GrpcExceptionParser {
    private static final Metadata.Key<String> ERROR_CODE_KEY = Metadata.Key.of("AxonIQ-ErrorCode", Metadata.ASCII_STRING_MARSHALLER);

    public static RuntimeException parse(Throwable ex) {
        String code = "AXONIQ-0001";
        if( ex instanceof StatusRuntimeException) {
            if(ex.getCause() instanceof EventStoreClientException) {
                return (EventStoreClientException)ex.getCause();
            }
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException)ex;
            if(Status.Code.UNIMPLEMENTED.equals(statusRuntimeException.getStatus().getCode()) ) {
                return new UnsupportedOperationException(ex.getMessage(), ex);
            }
            Metadata trailer = statusRuntimeException.getTrailers();
            String errorCode = trailer.get(ERROR_CODE_KEY);
            if (errorCode != null) {
                code = errorCode;
            }
        }
        return ErrorCode.getFromCode(code).convert(ex);
    }


}
