/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.quickstart;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Setting up the basic ToDoItem sample with a disruptor command and event bus and a file based event store. The
 * configuration takes place using spring. We use annotations to find the command and event handlers.
 *
 * @author Allard Buijze
 */
public class RunDisruptorCommandBusWithSpring {

    public static void main(String[] args) throws InterruptedException {
        // Load the spring beans from the xml configuration file.
        ConfigurableApplicationContext applicationContext = new ClassPathXmlApplicationContext("disruptor-config.xml");

        // Obtain the gateway from the context to send commands.
        CommandGateway commandGateway = applicationContext.getBean("commandGateway", CommandGateway.class);

        // and let's send some Commands on the CommandBus.
        CommandGenerator.sendCommands(commandGateway);

        // close the application context
        applicationContext.close();
    }
}
