package org.axonframework.deadline.jobrunnr;

import java.util.UUID;

public class DeadlineDetails {
    
    private String deadlineName;
    private UUID deadlineId;
    private String scopeDescription;
    private Object payload;
    private String[] keys;
    private Object[] values;
    
    private DeadlineDetails() {
    }
    
    public DeadlineDetails(String deadlineName, UUID deadlineId, String scopeDescription, Object payload, String[] keys, Object[] values) {
        this.deadlineName = deadlineName;
        this.deadlineId = deadlineId;
        this.scopeDescription = scopeDescription;
        this.payload = payload;
        this.keys = keys;
        this.values = values;
    }
    
    public String getDeadlineName() {
        return deadlineName;
    }
    
    public UUID getDeadlineId() {
        return deadlineId;
    }
    
    public String getScopeDescription() {
        return scopeDescription;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public String[] getKeys() {
        return keys;
    }
    
    public Object[] getValues() {
        return values;
    }
}
