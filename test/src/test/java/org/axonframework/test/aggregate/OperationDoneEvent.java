package org.axonframework.test.aggregate;

public class OperationDoneEvent {
    private String id;

    public OperationDoneEvent(String id) {
        this.id = id;
    }
}
