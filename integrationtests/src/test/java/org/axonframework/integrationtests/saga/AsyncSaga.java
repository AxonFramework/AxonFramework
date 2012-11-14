/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.integrationtests.saga;

import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.SagaEventHandler;
import org.axonframework.saga.annotation.StartSaga;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author Allard Buijze
 */
public class AsyncSaga extends AbstractAnnotatedSaga {

    private static final long serialVersionUID = -3189883877298625986L;
    private Deque<String> receivedMessages;

    public AsyncSaga() {
        this.receivedMessages = new LinkedBlockingDeque<String>();
    }

    @StartSaga
    @SagaEventHandler(associationProperty = "myId")
    public void handleSomeEvent(SagaStartEvent event) {
        receivedMessages.add(event.getMessage());
        associateWith("currentAssociation", event.getMyId().toString());
    }

    @SagaEventHandler(associationProperty = "currentAssociation")
    public void handleAssociatingEvent(SagaAssociationChangingEvent event) {
        removeAssociationWith("currentAssociation", event.getCurrentAssociation());
        associateWith("currentAssociation", event.getNewAssociation());
    }

    public Collection<String> getReceivedMessages() {
        return receivedMessages;
    }
}
