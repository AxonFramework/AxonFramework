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
