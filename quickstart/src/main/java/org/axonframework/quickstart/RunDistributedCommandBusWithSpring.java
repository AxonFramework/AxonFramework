/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.jgroups.JGroupsConnector;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.commandhandling.distributed.jgroups.JGroupsConnectorFactoryBean;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.config.EnableAxonAutoConfiguration;
import org.jgroups.stack.GossipRouter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.BindException;

/**
 * See {@link RunDistributedCommandBus}, only difference is that we use spring to wire all the beans.
 *
 * @author Jettro Coenradie
 */
public class RunDistributedCommandBusWithSpring {
    public static void main(String[] args) throws Exception {
        // Load the Load factor from the command line or use default 100
        Integer loadFactor = RunDistributedCommandBus.determineLoadFactor();

        System.setProperty("loadFactor", loadFactor.toString());

        // Start the GossipRouter if it is not already running
        GossipRouter gossipRouter = new GossipRouter();
        try {
            gossipRouter.start();
        } catch (BindException e) {
            System.out.println("Gossip router is already started in another JVM instance.");
        }

        // Load the spring beans from the xml configuration file.
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config.class);

        // Obtain the gateway from the context to send commands.
        CommandGateway commandGateway = applicationContext.getBean("commandGateway", CommandGateway.class);

        // Load the amount of times to send the commands from the command line or use default 1
        Integer numberOfCommandLoops = RunDistributedCommandBus.determineNumberOfCommandLoops();

        for (int i = 0; i < numberOfCommandLoops; i++) {
            CommandGenerator.sendCommands(commandGateway);
        }
    }

    @EnableAxonAutoConfiguration
    @Configuration
    public static class Config {

        @Primary
        @Bean
        public CommandBus commandBus(JGroupsConnector connector) throws Exception {
            DistributedCommandBus bus = new DistributedCommandBus(connector, connector);
            bus.updateLoadFactor(Integer.getInteger("loadFactor"));
            return bus;
        }

        @Bean
        public JGroupsConnectorFactoryBean jGroupsConnector(AxonConfiguration configuration) throws Exception {
            JGroupsConnectorFactoryBean jGroupsConnector = new JGroupsConnectorFactoryBean();
            jGroupsConnector.setClusterName("myChannel");
            jGroupsConnector.setConfiguration("tcp_gossip.xml");
            jGroupsConnector.setSerializer(configuration.serializer());
            return jGroupsConnector;
        }

        @Bean
        public CommandGateway commandGateway(CommandBus commandBus) throws Exception {
            return new DefaultCommandGateway(commandBus);
        }

        @Bean
        public RunDistributedCommandBus.ToDoLoggingCommandHandler commandHandler() {
            return new RunDistributedCommandBus.ToDoLoggingCommandHandler();
        }
    }
}
