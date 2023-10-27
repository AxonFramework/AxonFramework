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

package org.axonframework.messaging.annotation;

import org.axonframework.common.AxonConfigurationException;

import java.lang.reflect.Member;

/**
 * Thrown when an @...Handler annotated method was found that does not conform to the rules that apply to those
 * methods.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UnsupportedHandlerException extends AxonConfigurationException {

    private final Member violatingMethod;
    private static final long serialVersionUID = 7991150193173243668L;

    /**
     * Initialize the exception with a {@code message} and the {@code violatingMethod}.
     *
     * @param message         a descriptive message of the violation
     * @param violatingMethod the method that violates the rules of annotated Event Handlers
     */
    public UnsupportedHandlerException(String message, Member violatingMethod) {
        super(message);
        this.violatingMethod = violatingMethod;
    }

    /**
     * A reference to the method that violated the event handler rules.
     *
     * @return the method that violated the event handler rules
     */
    public Member getViolatingMethod() {
        return violatingMethod;
    }
}
