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

package org.axonframework.kafka.eventhandling.producer;

/**
 * Various Modes for publishing messages to kafka
 * <p>
 * Transactional: use kafka transactions while sending messages
 * WAIT_FOR_ACK : send messages and wait for acknowledgment (timeout can be configured)
 * NONE: Fire and forget
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public enum ConfirmationMode {
    TRANSACTIONAL, WAIT_FOR_ACK, NONE;

    public boolean isTransactional() {
        return this == TRANSACTIONAL;
    }

    public boolean isWaitForAck() {
        return this == WAIT_FOR_ACK;
    }
}
