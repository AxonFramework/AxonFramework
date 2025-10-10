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

package org.axonframework.test.fixture;

import org.axonframework.commandhandling.annotations.Command;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.commandhandling.monitoring.MonitoringCommandHandlerInterceptor;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.eventhandling.annotations.Event;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventhandling.monitoring.MonitoringEventDispatchInterceptor;
import org.axonframework.eventhandling.monitoring.MonitoringEventProcessor;
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.eventsourcing.annotations.EventSourcingHandler;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.annotations.InjectEntity;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.monitoring.MonitoringQueryBus;
import org.axonframework.test.fixture.AxonTestFixture.Customization;
import org.axonframework.test.fixture.AxonTestFixtureMonitoringTest.Domain.CourseAlreadyExists;
import org.axonframework.test.fixture.AxonTestFixtureMonitoringTest.Domain.CourseCreated;
import org.axonframework.test.fixture.AxonTestFixtureMonitoringTest.Domain.CreateCourse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.axonframework.test.fixture.AxonTestFixtureMonitoringTest.Domain.configurer;

/**
 * Configures MessageMonitors and verifies they have been correctly called when the sample domain ran.
 */
public class AxonTestFixtureMonitoringTest {

    private static final Logger logger = LoggerFactory.getLogger(AxonTestFixtureMonitoringTest.class);

    /**
     * Singleton {@link MessageMonitor} that logs reports.
     */
    public enum LoggingMessageMonitor implements MessageMonitor<Message> {
        INSTANCE;

        @Override
        public MonitorCallback onMessageIngested(final @NotNull Message message) {
            return new MonitorCallback() {
                @Override
                public void reportSuccess() {
                    logger.info("Report Success: message={}", message);
                }

                @Override
                public void reportFailure(Throwable cause) {
                    logger.error("Report Failure: message={}, cause={}", message, cause);
                }

                @Override
                public void reportIgnored() {
                    logger.info("Report Ignored: message={}", message);
                }
            };
        }
    }

    enum Domain {
        ;

        // avoid redundant lambda magic for recurrent constructor signatures
        static <C, D extends C> DecoratorDefinition<C, D> decorate(Class<C> component,
                                                                   MessageMonitor<Message> messageMonitor,
                                                                   BiFunction<C, MessageMonitor<Message>, D> decoratorFactory) {
            return DecoratorDefinition.forType(component)
                                      .with((config, name, delegate) -> decoratorFactory.apply(delegate,
                                                                                               messageMonitor))
                                      .order(0);
        }


        // configure application using given messageMonitor
        static EventSourcingConfigurer configurer(final MessageMonitor<Message> messageMonitor) {


            final var configurer = EventSourcingConfigurer.create()

                                                          .componentRegistry(registry -> {


//                                                              registry
//                                                                      .registerDecorator(decorate(EventProcessor.class,
//                                                                                                  messageMonitor,
//                                                                                                  MonitoringEventProcessor::new))
//                                                                      .registerDecorator(decorate(QueryBus.class,
//                                                                                                  messageMonitor,
//                                                                                                  MonitoringQueryBus::new))
//                                                              ;
                                                          });

            final var entity = EventSourcedEntityModule.annotated(String.class,
                                                                  Domain.CourseCreatedCommandHandler.State.class);

            return configurer.registerEntity(entity)
                             .messaging(messagingConfigurer -> {
                                 messagingConfigurer.registerCommandHandlerInterceptor(
                                         c -> new MonitoringCommandHandlerInterceptor(LoggingMessageMonitor.INSTANCE)
                                 ).registerEventDispatchInterceptor(
                                         c -> new MonitoringEventDispatchInterceptor(LoggingMessageMonitor.INSTANCE)
                                 );
                             })
                             .registerCommandHandlingModule(CommandHandlingModule.named("CreateCourse")
                                                                                 .commandHandlers()
                                                                                 .annotatedCommandHandlingComponent(c -> new Domain.CourseCreatedCommandHandler()))
                    ;
        }


        public static final String COURSE_ID = "courseId";


        @Command(name = "CreateCourse")
        public record CreateCourse(String courseId, String name) {

        }

        @Event(name = "CreateCourse")
        public record CourseCreated(@EventTag(key = COURSE_ID) String courseId, String name) {

        }

        public record JustSomeAdditionalCourseCreated(String courseId, String name) {

        }

        public static class CourseAlreadyExists extends RuntimeException {

            public CourseAlreadyExists(String courseId) {
                super("Course with courseId=%s already exists".formatted(courseId));
            }
        }

        static class CourseCreatedCommandHandler {

            @CommandHandler
            void handle(CreateCourse cmd, @InjectEntity(idProperty = COURSE_ID) CourseCreatedCommandHandler.State state,
                        EventAppender eventAppender) {
                eventAppender.append(state.decide(cmd));
            }

            @EventSourcedEntity(tagKey = COURSE_ID)
            static class State {

                private boolean created;

                @EntityCreator
                public State() {
                    this.created = false;
                }

                public List<Object> decide(CreateCourse cmd) {
                    if (created) {
                        throw new CourseAlreadyExists(cmd.courseId);
                    }
                    return List.of(new CourseCreated(cmd.courseId(), cmd.name()),
                                   new JustSomeAdditionalCourseCreated(cmd.courseId(), cmd.name()));
                }

                @EventSourcingHandler
                public State evolve(CourseCreated event) {
                    this.created = true;
                    return this;
                }
            }
        }
    }

    @Test
    void registeredCommandMessageMonitor() {
        final var courseId = UUID.randomUUID().toString();
        final var fixture = AxonTestFixture.with(
                configurer(LoggingMessageMonitor.INSTANCE),
                Customization::disableAxonServer
        );

        // TODO: logging timestamps show that the reports are not in order of execution. Issue?
        fixture
                .given()
                .noPriorActivity()
                .when()
                .command(new CreateCourse(courseId, "Math"))
                .then()
                .success()
                .events(new CourseCreated(courseId, "Math"),
                        new Domain.JustSomeAdditionalCourseCreated(courseId, "Math"))
                .and()
                .when()
                .command(new CreateCourse(courseId, "Math"))
                .then()
                .noEvents()
                .exception(CourseAlreadyExists.class);
        ;
    }
}
