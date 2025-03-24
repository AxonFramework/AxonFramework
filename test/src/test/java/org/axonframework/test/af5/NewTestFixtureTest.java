/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.test.af5;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewTestFixtureTest {

    @Test
    void test() {
//        var handledMessage = new ArrayList<>();
//
//        var configurer = MessagingConfigurer.create();
//        var fixture = new NewTestFixture(configurer);
//
//        var config = configurer.build();
//        var commandBus = config.getComponent(CommandBus.class);
//        commandBus.subscribe(new QualifiedName("test"), (command, context) -> {
//            handledMessage.add(command);
//            return MessageStream.just(new GenericCommandResultMessage<>(command));
//        });
//        commandBus.dispatch()
    }
}