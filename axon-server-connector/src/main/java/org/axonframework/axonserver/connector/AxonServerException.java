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

package org.axonframework.axonserver.connector;


import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.common.AxonException;

import java.util.Collection;
import java.util.Collections;

/**
 * Generic exception indicating an error related to AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class AxonServerException extends AxonException {

    private final String errorCode;
    private final String source;
    private final Collection<String> details;

    public AxonServerException(String errorCode, ErrorMessage errorMessage) {
        this(errorMessage.getMessage(), errorCode, errorMessage.getLocation(), errorMessage.getDetailsList());
    }

    public AxonServerException(String errorCode, String message) {
        this(message, errorCode, null, Collections.emptyList());
    }

    public AxonServerException(String message,
                               String errorCode,
                               String source,
                               Collection<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.source = source;
        this.details = details;
    }

    public String errorCode() {
        return errorCode;
    }

    public String source() {
        return source;
    }

    public Collection<String> details() {
        return details;
    }


}
