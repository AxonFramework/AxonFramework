/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.junit.*;

import static org.junit.Assert.*;

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
        AnnotationCommandHandlerAdapter.subscribe(this, scb);

        scb.dispatch("Hello", new VoidCallback() {
            @Override
            protected void onSuccess() {
                // what I expected
            }

            @Override
            public void onFailure(Throwable cause) {
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
