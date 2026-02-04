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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.modelling.entity.ChildEntityNotFoundException;
import org.axonframework.modelling.entity.EntityAlreadyExistsForCreationalCommandHandlerException;
import org.axonframework.modelling.entity.WrongPolymorphicEntityTypeException;
import org.axonframework.modelling.entity.child.ChildAmbiguityException;
import org.axonframework.modelling.entity.domain.development.Developer;
import org.axonframework.modelling.entity.domain.development.InternalProject;
import org.axonframework.modelling.entity.domain.development.Marketeer;
import org.axonframework.modelling.entity.domain.development.OpenSourceProject;
import org.axonframework.modelling.entity.domain.development.Project;
import org.axonframework.modelling.entity.domain.development.commands.AssignDeveloperAsLeadDeveloper;
import org.axonframework.modelling.entity.domain.development.commands.AssignDeveloperToProject;
import org.axonframework.modelling.entity.domain.development.commands.AssignMarketeer;
import org.axonframework.modelling.entity.domain.development.commands.ChangeDeveloperGithubUsername;
import org.axonframework.modelling.entity.domain.development.commands.ChangeMarketeerHubspotUsername;
import org.axonframework.modelling.entity.domain.development.commands.CreateProjectCommand;
import org.axonframework.modelling.entity.domain.development.commands.RenameProjectCommand;
import org.axonframework.modelling.entity.domain.development.common.ProjectType;
import org.axonframework.modelling.entity.domain.development.events.DeveloperGithubUsernameChanged;
import org.axonframework.modelling.entity.domain.development.events.LeadDeveloperAssigned;
import org.axonframework.modelling.entity.domain.development.events.MarketeerAssigned;
import org.axonframework.modelling.entity.domain.development.events.MarketeerHubspotUsernameChanged;
import org.axonframework.modelling.entity.domain.development.events.ProjectCreatedEvent;
import org.axonframework.modelling.entity.domain.development.events.ProjectRenamedEvent;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the {@link AnnotatedEntityMetamodel} through the {@link Project} domain model. This domain model has been
 * designed to touch as many aspects of the {@link AnnotatedEntityMetamodel} as possible, such as polymorphic types,
 * command routing, and event publication.
 * <p>
 * Note that the domain might not be feature-complete or realistic. In addition, while the model is not event-sourced
 * but state-sourced, it does apply events that are then applied to the model state. This is done to ensure that the
 * model behaves as expected and that the events are published correctly. This allows us to assert both the events
 * published and the state of the model after the commands have been handled.
 *
 * @author Mitchell Herrijgers
 */
class PolymorphicAnnotatedEntityMetamodelTest extends AbstractAnnotatedEntityMetamodelTest<Project> {

    @Override
    protected AnnotatedEntityMetamodel<Project> getMetamodel() {
        return AnnotatedEntityMetamodel.forPolymorphicType(
                Project.class,
                Set.of(InternalProject.class, OpenSourceProject.class),
                parameterResolverFactory,
                messageTypeResolver,
                messageConverter,
                eventConverter
        );
    }

    @Nested
    @DisplayName("Project Creation and Management Tests")
    class ProjectManagementTests {

        @Test
        void canCreateProject() {
            // Given no earlier state
            entityState = null;

            // When
            var createdIdentifier = dispatchCreateCommand(new CreateProjectCommand("Axon Framework 5",
                                                                                   ProjectType.INTERNAL));

            // Then
            assertThat(createdIdentifier).isInstanceOf(String.class);
            assertThat(publishedEvents).containsExactly(
                    new ProjectCreatedEvent((String) createdIdentifier, "Axon Framework 5", ProjectType.INTERNAL)
            );
        }

        /**
         * Tests that a handler on the abstract type {@link Project} can be invoked, which is the case for
         * {@link RenameProjectCommand}.
         */
        @Test
        void canRenameProject() {
            // Given
            entityState = new InternalProject("project-id", "Axon Framework 5");

            // When
            dispatchInstanceCommand(new RenameProjectCommand("project-id", "Axon Framework 6"));

            // Then
            assertThat(publishedEvents).containsExactly(
                    new ProjectRenamedEvent("project-id", "Axon Framework 6")
            );
            assertThat(entityState.getName()).isEqualTo("Axon Framework 6");
        }

        @Test
        void canNotCreateProjectWhenAlreadyExists() {
            // Given an existing project
            entityState = new InternalProject("project-id", "Axon Framework 5");

            // When & Then
            assertThatExceptionOfType(EntityAlreadyExistsForCreationalCommandHandlerException.class)
                    .isThrownBy(() -> dispatchInstanceCommand(new CreateProjectCommand("Axon Framework 6",
                                                                                       ProjectType.INTERNAL))

                    );
        }
    }

    @Nested
    @DisplayName("Command Resolution Tests")
    class CommandResolutionTests {

        @Test
        void detectsAllInstanceCommands() {
            // Given
            entityState = new InternalProject("project-id", "Axon Framework 5");

            // When
            var instanceCommands = metamodel.supportedInstanceCommands();

            // Then
            assertThat(instanceCommands).containsAll(List.of(
                    qualifiedName(RenameProjectCommand.class),
                    qualifiedName(AssignDeveloperToProject.class),
                    qualifiedName(AssignDeveloperAsLeadDeveloper.class),
                    qualifiedName(AssignMarketeer.class)
            ));
        }

        @Test
        void detectsAllCreationalCommands() {
            // When
            var createCommands = metamodel.supportedCreationalCommands();

            // Then
            assertThat(createCommands).containsAll(List.of(
                    qualifiedName(CreateProjectCommand.class)
            ));
        }

        @Test
        void resolvesCorrectRepresentationForOpenSourceProjectModelCommand() {
            // When & Then
            var expectedRepresentation = metamodel.getExpectedRepresentation(qualifiedName(AssignMarketeer.class));
            assertThat(expectedRepresentation).isEqualTo(AssignMarketeer.class);
        }

        @Test
        void resolvesCorrectRepresentationForOpenSourceProjectModelEvent() {
            // When & Then
            var expectedRepresentation = metamodel.getExpectedRepresentation(qualifiedName(MarketeerAssigned.class));
            assertThat(expectedRepresentation).isEqualTo(MarketeerAssigned.class);
        }

        @Test
        void resolvesCorrectRepresentationForAbstractProjectModelCommand() {
            // When & Then
            var expectedRepresentation = metamodel.getExpectedRepresentation(qualifiedName(AssignDeveloperToProject.class));
            assertThat(expectedRepresentation).isEqualTo(AssignDeveloperToProject.class);
        }

        @Test
        void resolvesCorrectRepresentationForAbstractProjectModelEvent() {
            // When & Then
            var expectedRepresentation = metamodel.getExpectedRepresentation(qualifiedName(ProjectRenamedEvent.class));
            assertThat(expectedRepresentation).isEqualTo(ProjectRenamedEvent.class);
        }
    }

    @Nested
    @DisplayName("Marketeer Management Tests")
    class MarketeerTests {

        /**
         * Tests that using a command that is only defined on the {@link OpenSourceProject} type, such as
         * {@link AssignMarketeer}, is correctly able to be executed on an instance of that type.
         */
        @Test
        void canAssignMarketeerToOpenSourceProject() {
            // Given an existing open source project
            entityState = new OpenSourceProject("project-id", "Axon Framework 5");

            // When
            dispatchInstanceCommand(new AssignMarketeer("project-id", "aad@axoniq.io", "Aad"));

            // Then
            assertThat(publishedEvents).containsExactly(
                    new MarketeerAssigned("project-id", "aad@axoniq.io", "Aad")
            );
            assertThat(((OpenSourceProject) entityState).getMarketeer().getEmail()).isEqualTo("aad@axoniq.io");
            assertThat(((OpenSourceProject) entityState).getMarketeer().getHubspotUsername()).isEqualTo("Aad");
        }

        /**
         * Tests that assigning a marketeer to an internal project fails, as the {@link InternalProject} does not
         * support this operation. Only the {@link OpenSourceProject} supports assigning a marketeer.
         */
        @Test
        void canNotAssignMarketeerToInternalProject() {
            // Given an existing internal project
            entityState = new InternalProject("project-id", "Axon Framework 5");

            // When & Then
            assertThatExceptionOfType(WrongPolymorphicEntityTypeException.class)
                    .isThrownBy(() -> dispatchInstanceCommand(new AssignMarketeer("project-id",
                                                                                  "aad@axoniq.io",
                                                                                  "Aad")));
        }

        /**
         * Tests that, even without a routing key on a single child entity, it is routed correctly. In addition, this
         * tests that a child entity can be reached that is only defined on one of the concrete types of the polymorphic
         * entity model. As marketeers are records, this uses an immutable event handler that returns a new instance.
         */
        @Test
        void canChangeHubspotUsernameOfMarketeer() {
            // Given an existing open source project with a marketeer
            entityState = new OpenSourceProject("project-id", "Axon Framework 5");
            dispatchInstanceCommand(new AssignMarketeer("project-id", "aad@axoniq.io", "aad"));
            publishedEvents.clear();

            // When
            dispatchInstanceCommand(new ChangeMarketeerHubspotUsername("project-id", "aad@axoniq.io", "aad-hubspot"));

            // Then
            assertThat(publishedEvents).containsExactly(
                    new MarketeerHubspotUsernameChanged("project-id", "aad@axoniq.io", "aad-hubspot")
            );
            Marketeer marketeer = ((OpenSourceProject) entityState).getMarketeer();
            assertThat(marketeer.getEmail()).isEqualTo("aad@axoniq.io");
            assertThat(marketeer.getHubspotUsername()).isEqualTo("aad-hubspot");
        }
    }

    @Nested
    @DisplayName("Developer Management Tests")
    class DeveloperTests {

        /**
         * Tests that assigning a developer to an open source project works correctly.
         */
        @Test
        void canAssignDeveloperAsLeadDeveloper() {
            // Given an existing open source project with a developer
            setupAxonFramework5ProjectWithDevelopers(false);

            // When
            dispatchInstanceCommand(new AssignDeveloperAsLeadDeveloper("project-id", "steven.vanbeelen@axoniq.io"));

            // Then
            assertThat(publishedEvents).containsExactly(
                    new LeadDeveloperAssigned("project-id", "steven.vanbeelen@axoniq.io"));

            assertThat(entityState.getLeadDeveloper().email()).isEqualTo("steven.vanbeelen@axoniq.io");
            assertThat(entityState.getOtherDevelopers()).extracting("email")
                                                        .doesNotContain("steven.vanbeelen@axoniq.io");
        }

        /**
         * Tests that changing the GitHub username of a regular developer works correctly. In other words, it tests
         * whether it chooses the matching field out of the two in the parent class.
         */
        @Test
        void canChangeGithubUsernameOfRegularDeveloper() {
            // Given an existing open source project with developers
            setupAxonFramework5ProjectWithDevelopers(true);

            // When
            dispatchInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                      "mitchell.herrijgers@axoniq.io",
                                                                      "CodeDrivenMitch-two"));

            // Then
            assertThat(publishedEvents).containsExactly(
                    new DeveloperGithubUsernameChanged("project-id",
                                                       "mitchell.herrijgers@axoniq.io",
                                                       "CodeDrivenMitch",
                                                       "CodeDrivenMitch-two"));

            Developer developer = entityState.getOtherDevelopers()
                                             .stream()
                                             .filter(c -> c.email().equals("mitchell.herrijgers@axoniq.io"))
                                             .findAny()
                                             .orElseThrow();
            assertThat(developer.githubUsername()).isEqualTo("CodeDrivenMitch-two");
        }

        /**
         * Tests that changing the GitHub username of the lead developer works correctly. In other words, it tests
         * whether it chooses the matching field out of the two in the parent class.
         */
        @Test
        void canChangeGithubUsernameOfLeadDeveloper() {
            // Given an existing open source project with developers
            setupAxonFramework5ProjectWithDevelopers(true);

            // When
            dispatchInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                      "steven.vanbeelen@axoniq.io",
                                                                      "smcvb-two"));

            // Then
            assertThat(publishedEvents).containsExactly(
                    new DeveloperGithubUsernameChanged("project-id",
                                                       "steven.vanbeelen@axoniq.io",
                                                       "smcvb",
                                                       "smcvb-two"));

            Developer leadDeveloper = entityState.getLeadDeveloper();
            assertThat(leadDeveloper.githubUsername()).isEqualTo("smcvb-two");
        }

        @Test
        void failsIfMultipleDevelopersOfSameEmailExistWithinSameCollection() {
            // In this case, we need to set wrong state so the command goes wrong
            entityState = new OpenSourceProject("project-id", "Axon Framework 5");
            entityState.getOtherDevelopers().add(new Developer("mitchell.herrijgers@axoniq.io", "CodeDrivenMitch"));
            entityState.getOtherDevelopers().add(new Developer("mitchell.herrijgers@axoniq.io", "CodeDrivenMitch"));

            // When & Then
            assertThatExceptionOfType(ChildAmbiguityException.class)
                    .isThrownBy(() -> dispatchInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                                                "mitchell.herrijgers@axoniq.io",
                                                                                                "CodeDrivenMitch-two")));
        }

        @Test
        void failsIfMultipleDevelopersOfSameEmailExistWithinBothMembers() {
            // In this case, we need to set wrong state so the command goes wrong
            entityState = new OpenSourceProject("project-id", "Axon Framework 5");
            entityState.setLeadDeveloper(new Developer("steven.vanbeelen@axoniq.io", "smcvb"));
            entityState.getOtherDevelopers().add(new Developer("steven.vanbeelen@axoniq.io", "smcvb"));

            // When & Then
            assertThatExceptionOfType(ChildAmbiguityException.class)
                    .isThrownBy(() -> dispatchInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                                                "steven.vanbeelen@axoniq.io",
                                                                                                "smcvb-two")));
        }

        @Test
        void failsWhenTryingToChangeDeveloperThatDoesNotExist() {
            // Given an existing project with developers
            setupAxonFramework5ProjectWithDevelopers(true);

            // When & Then - trying to change a developer that doesn't exist
            assertThatExceptionOfType(ChildEntityNotFoundException.class)
                    .isThrownBy(() -> dispatchInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                                                "nonexistent@example.com",
                                                                                                "new-username")));
        }
    }

    /**
     * Sets up a base state for the Axon Framework 5 project, with several developers assigned to it.
     */
    private void setupAxonFramework5ProjectWithDevelopers(boolean assignLeadDeveloper) {
        entityState = new OpenSourceProject("project-id", "Axon Framework 5");
        dispatchInstanceCommand(new AssignDeveloperToProject("project-id", "steven.vanbeelen@axoniq.io", "smcvb"));
        dispatchInstanceCommand(new AssignDeveloperToProject("project-id",
                                                             "mitchell.herrijgers@axoniq.io",
                                                             "CodeDrivenMitch"));
        dispatchInstanceCommand(new AssignDeveloperToProject("project-id",
                                                             "mateusz.nowak@axoniq.io",
                                                             "MateuszNaKodach"));
        dispatchInstanceCommand(new AssignDeveloperToProject("project-id", "allard@axoniq.io", "abuijze"));
        if (assignLeadDeveloper) {
            dispatchInstanceCommand(new AssignDeveloperAsLeadDeveloper("project-id", "steven.vanbeelen@axoniq.io"));
        }
        publishedEvents.clear();
    }
}