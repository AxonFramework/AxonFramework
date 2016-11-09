package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axon.amqp")
public class AMQPProperties {

    /**
     * Name of the exchange to forward Event messages to
     */
    private String exchange;

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }
}
