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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.*;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * @author Allard Buijze
 */
public class VoidCallbackTest {

    /**
     * This test should make sure the problem described in issue #91 does not occur anymore
     */
    @Test
    public void testCallbackCalled() {
        SimpleCommandBus scb = new SimpleCommandBus();
        new AnnotationCommandHandlerAdapter(this).subscribe(scb);

        scb.dispatch(GenericCommandMessage.asCommandMessage("Hello"), new VoidCallback<Object>() {
            @Override
            protected void onSuccess(CommandMessage<?> commandMessage) {
                // what I expected
            }

            @Override
            public void onFailure(CommandMessage commandMessage, Throwable cause) {
                cause.printStackTrace();
                fail("Did not expect a failure");
            }
        });
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @CommandHandler
    public void handle(String someCommand) {
        // nothing to do
    }
}
