/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.deadline.jobrunnr;

import org.axonframework.messaging.ScopeDescriptor;

import java.util.UUID;

public class DeadlineDetails {
    
    private String deadlineName;
    private UUID deadlineId;
    private ScopeDescriptor scopeDescription;
    private Object payload;
    private String[] keys;
    private Object[] values;
    
    private DeadlineDetails() {
    }

    public DeadlineDetails(String deadlineName, UUID deadlineId, ScopeDescriptor scopeDescription, Object payload,
                           String[] keys, Object[] values) {
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

    public ScopeDescriptor getScopeDescription() {
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
