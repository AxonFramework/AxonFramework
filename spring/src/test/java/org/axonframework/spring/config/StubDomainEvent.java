package org.axonframework.spring.config;

import java.io.Serializable;

public class StubDomainEvent implements Serializable {

    private static final long serialVersionUID = 834667054977749990L;

    @Override
    public String toString() {
        return "StubDomainEvent";
    }
}