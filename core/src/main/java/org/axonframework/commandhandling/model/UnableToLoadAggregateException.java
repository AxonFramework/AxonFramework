package org.axonframework.commandhandling.model;

public class UnableToLoadAggregateException extends RuntimeException {
    public UnableToLoadAggregateException() {
        super("This repository is unable to load any aggregate");
    }
}
