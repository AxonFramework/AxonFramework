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

package org.axonframework.sample.consoleui;

import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.sample.app.command.ContactCommandHandler;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class ConsoleLauncher {

    @Autowired
    private ContactCommandHandler commandHandler;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private ContactRepository repository;

    public static void main(String... args) throws InterruptedException {
        ConsoleLauncher launcher = new ConsoleLauncher();
        launcher.initializeContext();
        launcher.processCommands(System.in);
        launcher.shutDown();
    }

    public void processCommands(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream);
        String command = null;
        while (!"quit".equals(command)) {
            System.out.print("> ");
            command = scanner.nextLine();
            if (command.startsWith("add ")) {
                UUID id = commandHandler.createContact(command.substring(4));
                System.out.println("Contact created: " + id.toString());
            } else if (command.startsWith("list")) {
                List<ContactEntry> contacts = repository.findAllContacts();
                for (ContactEntry entry : contacts) {
                    System.out.println(entry.getIdentifier() + " : " + entry.getName());
                }
            } else if (command.startsWith("quit")) {
                // will automatically quit
            } else {
                System.out.println("'add <name>' to add a contact.");
                System.out.println("'list' to list all contact.");
                System.out.println("'quit' to quit.");
            }
        }
        scanner.close();
    }

    public void initializeContext() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
                new String[]{
                        "/META-INF/spring/application-context.xml",
                        "/META-INF/spring/database-context.xml",
                        "/META-INF/spring/datainit-context.xml"
                });
        ConfigurableListableBeanFactory factory = (ConfigurableListableBeanFactory) context
                .getAutowireCapableBeanFactory();
        factory.autowireBean(this);
        eventBus.subscribe(new ConsoleEventWriter());
    }

    public void shutDown() throws InterruptedException {
        System.exit(0);
    }
}
