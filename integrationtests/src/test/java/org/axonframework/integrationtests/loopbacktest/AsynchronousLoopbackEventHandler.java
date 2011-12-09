/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.integrationtests.loopbacktest;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.eventhandling.FullConcurrencyPolicy;
import org.axonframework.eventhandling.annotation.AsynchronousEventListener;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.springframework.beans.factory.annotation.Autowired;

import static org.axonframework.commandhandling.annotation.GenericCommandMessage.asCommandMessage;

/**
 * @author Allard Buijze
 */
@AsynchronousEventListener(sequencingPolicyClass = FullConcurrencyPolicy.class)
public class AsynchronousLoopbackEventHandler {

    public static final String MAGIC_WORDS = "get more!";

    @Autowired
    private CommandBus commandBus;

    @EventHandler
    public void handleStubDomainEvent(MessageCreatedEvent event) {
        if (MAGIC_WORDS.equals(event.getMessageContents())) {
            commandBus.dispatch(asCommandMessage("I got the magic words"), new VoidCallback() {
                @Override
                protected void onSuccess() {
                }

                @Override
                public void onFailure(Throwable cause) {
                    cause.printStackTrace();
                }
            });
        }
    }
}
