/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axon.amqp")
public class AMQPProperties {

    /**
     * Name of the exchange to forward Event messages to
     */
    private String exchange;

    /**
     * Whether the messages should be sent with the 'durable' flag. Defaults to true.
     */
    private boolean durableMessages = true;

    /**
     * Defines how transactions around publishing should be managed (none (default), transactional or publisher-ack).
     */
    private TransactionMode transactionMode = TransactionMode.NONE;

    public String getExchange() {
        return exchange;
    }
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public TransactionMode getTransactionMode() {
        return transactionMode;
    }
    public void setTransactionMode(TransactionMode transactionMode) {
        this.transactionMode = transactionMode;
    }

    public boolean isDurableMessages() {
        return durableMessages;
    }
    public void setDurableMessages(boolean durableMessages) {
        this.durableMessages = durableMessages;
    }

    public enum TransactionMode {

        TRANSACTIONAL,
        PUBLISHER_ACK,
        NONE

    }
}
