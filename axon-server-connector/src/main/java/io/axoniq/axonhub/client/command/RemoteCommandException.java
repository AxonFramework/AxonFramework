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
package io.axoniq.axonhub.client.command;

import io.axoniq.axonhub.ErrorMessage;

/**
 * @author Marc Gathier
 */
public class RemoteCommandException extends Throwable {

    private final String errorCode;
    private final String location;
    private final Iterable<String> details;
    public RemoteCommandException(String errorCode, ErrorMessage message) {
        super(message.getMessage());
        this.errorCode = errorCode;
        details = message.getDetailsList();
        location = message.getLocation();
    }

    public Iterable<String> getDetails() {
        return details;
    }

    public String getLocation() {
        return location;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "RemoteCommandException{" +
                "message=" + getMessage() +
                ", errorCode='" + errorCode + '\'' +
                ", location='" + location + '\'' +
                ", details=" + details +
                '}';
    }
}
