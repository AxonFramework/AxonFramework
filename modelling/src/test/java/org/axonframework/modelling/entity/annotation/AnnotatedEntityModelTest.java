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

import org.axonframework.modelling.entity.EntityExistsForCreationalCommandHandler;
import org.axonframework.modelling.entity.WrongPolymorphicEntityTypeException;
import org.axonframework.modelling.entity.child.ChildAmbiguityException;
import org.axonframework.modelling.entity.domain.Developer;
import org.axonframework.modelling.entity.domain.InternalProject;
import org.axonframework.modelling.entity.domain.Marketeer;
import org.axonframework.modelling.entity.domain.OpenSourceProject;
import org.axonframework.modelling.entity.domain.Project;
import org.axonframework.modelling.entity.domain.commands.AssignDeveloperAsLeadDeveloper;
import org.axonframework.modelling.entity.domain.commands.AssignDeveloperToProject;
import org.axonframework.modelling.entity.domain.commands.AssignMarketeer;
import org.axonframework.modelling.entity.domain.commands.ChangeDeveloperGithubUsername;
import org.axonframework.modelling.entity.domain.commands.ChangeMarketeerHubspotUsername;
import org.axonframework.modelling.entity.domain.commands.CreateProjectCommand;
import org.axonframework.modelling.entity.domain.commands.RenameProjectCommand;
import org.axonframework.modelling.entity.domain.common.ProjectType;
import org.axonframework.modelling.entity.domain.events.DeveloperGithubUsernameChanged;
import org.axonframework.modelling.entity.domain.events.LeadDeveloperAssigned;
import org.axonframework.modelling.entity.domain.events.MarketeerAssigned;
import org.axonframework.modelling.entity.domain.events.MarketeerHubspotUsernameChanged;
import org.axonframework.modelling.entity.domain.events.ProjectCreatedEvent;
import org.axonframework.modelling.entity.domain.events.ProjectRenamedEvent;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the {@link AnnotatedEntityModel} through the {@link Project} domain model. This domain model has been designed
 * to touch as many aspects of the {@link AnnotatedEntityModel} as possible, such as polymorphic types, command routing,
 * and event publication.
 * <p>
 * Note that the domain might not be feature-complete or realistic. In addition, while the model is not event-sourced
 * but state-sourced, it does apply events that are then applied to the model state. This is done to ensure that the
 * model behaves as expected and that the events are published correctly. This allows us to assert both the events
 * published and the state of the model after the commands have been handled.
 *
 * @author Mitchell Herrijgers
 */
class AnnotatedEntityModelTest extends AbstractAnnotatedEntityModelTest<Project> {

    @Override
    protected AnnotatedEntityModel<Project> getModel() {
        return AnnotatedEntityModel.forPolymorphicType(
                Project.class,
                Set.of(InternalProject.class, OpenSourceProject.class),
                parameterResolverFactory,
                messageTypeResolver
        );
    }

    @Test
    void canCreateProject() {
        // Given no earlier state
        modelState = null;

        // When
        var createdIdentifier = handleCreateCommand(new CreateProjectCommand("Axon Framework 5", ProjectType.INTERNAL));

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
        modelState = new InternalProject("project-id", "Axon Framework 5");

        // When
        handleInstanceCommand(new RenameProjectCommand("project-id", "Axon Framework 6"));

        // Then
        assertThat(publishedEvents).containsExactly(
                new ProjectRenamedEvent("project-id", "Axon Framework 6")
        );
        assertThat(modelState.getName()).isEqualTo("Axon Framework 6");
    }

    @Test
    void canNotCreateProjectWhenAlreadyExists() {
        // Given an existing project
        modelState = new InternalProject("project-id", "Axon Framework 5");

        // When & Then
        assertThatExceptionOfType(EntityExistsForCreationalCommandHandler.class)
                .isThrownBy(() -> handleInstanceCommand(new CreateProjectCommand("Axon Framework 6",
                                                                                 ProjectType.INTERNAL))

                );
    }

    @Test
    void detectsAllInstanceCommands() {
        // Given
        modelState = new InternalProject("project-id", "Axon Framework 5");

        // When
        var instanceCommands = model.supportedInstanceCommands();

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
        var createCommands = model.supportedCreationalCommands();

        // Then
        assertThat(createCommands).containsAll(List.of(
                qualifiedName(CreateProjectCommand.class)
        ));
    }

    @Test
    void resolvesCorrectRepresentationForOpenSourceProjectModelCommand() {
        // When & Then
        var expectedRepresentation = model.getExpectedRepresentation(qualifiedName(AssignMarketeer.class));
        assertThat(expectedRepresentation).isEqualTo(AssignMarketeer.class);
    }

    @Test
    void resolvesCorrectRepresentationForOpenSourceProjectModelEvent() {
        // When & Then
        var expectedRepresentation = model.getExpectedRepresentation(qualifiedName(MarketeerAssigned.class));
        assertThat(expectedRepresentation).isEqualTo(MarketeerAssigned.class);
    }

    @Test
    void resolvesCorrectRepresentationForAbstractProjectModelCommand() {
        // When & Then
        var expectedRepresentation = model.getExpectedRepresentation(qualifiedName(AssignDeveloperToProject.class));
        assertThat(expectedRepresentation).isEqualTo(AssignDeveloperToProject.class);
    }

    @Test
    void resolvesCorrectRepresentationForAbstractProjectModelEvent() {
        // When & Then
        var expectedRepresentation = model.getExpectedRepresentation(qualifiedName(ProjectRenamedEvent.class));
        assertThat(expectedRepresentation).isEqualTo(ProjectRenamedEvent.class);
    }

    /**
     * Tests that using a command that is only defined on the {@link OpenSourceProject} type, such as
     * {@link AssignMarketeer}, is correctly able to be executed on an instance of that type.
     */
    @Test
    void canAssignMarketeerToOpenSourceProject() {
        // Given an existing open source project
        modelState = new OpenSourceProject("project-id", "Axon Framework 5");

        // When
        handleInstanceCommand(new AssignMarketeer("project-id", "aad@axoniq.io", "Aad"));

        // Then
        assertThat(publishedEvents).containsExactly(
                new MarketeerAssigned("project-id", "aad@axoniq.io", "Aad")
        );
        assertThat(((OpenSourceProject) modelState).getMarketeer().getEmail()).isEqualTo("aad@axoniq.io");
        assertThat(((OpenSourceProject) modelState).getMarketeer().getHubspotUsername()).isEqualTo("Aad");
    }

    /**
     * Tests that assigning a marketeer to an internal project fails, as the {@link InternalProject} does not support
     * this operation. Only the {@link OpenSourceProject} supports assigning a marketeer.
     */
    @Test
    void canNotAssignMarketeerToInternalProject() {
        // Given an existing internal project
        modelState = new InternalProject("project-id", "Axon Framework 5");

        // When & Then
        assertThatExceptionOfType(WrongPolymorphicEntityTypeException.class)
                .isThrownBy(() -> handleInstanceCommand(new AssignMarketeer("project-id", "aad@axoniq.io", "Aad")));
    }

    /**
     * Tests that, even without a routing key on a single child entity, it is routed correctly. In addition, this tests
     * that a child entity can be reached that is only defined on one of the concrete types of the polymorphic entity
     * model. As marketeers are records, this uses an immutable event handler that returns a new instance.
     */
    @Test
    void canChangeHubspotUsernameOfMarketeer() {
        // Given an existing open source project with a marketeer
        modelState = new OpenSourceProject("project-id", "Axon Framework 5");
        handleInstanceCommand(new AssignMarketeer("project-id", "aad@axoniq.io", "aad"));
        publishedEvents.clear();

        // When
        handleInstanceCommand(new ChangeMarketeerHubspotUsername("project-id", "aad@axoniq.io", "aad-hubspot"));

        // Then
        assertThat(publishedEvents).containsExactly(
                new MarketeerHubspotUsernameChanged("project-id", "aad@axoniq.io", "aad-hubspot")
        );
        Marketeer marketeer = ((OpenSourceProject) modelState).getMarketeer();
        assertThat(marketeer.getEmail()).isEqualTo("aad@axoniq.io");
        assertThat(marketeer.getHubspotUsername()).isEqualTo("aad-hubspot");
    }

    /**
     * Tests that assigning a developer to an open source project works correctly.
     */
    @Test
    void canAssignDeveloperAsLeadDeveloper() {
        // Given an existing open source project with a developer
        setupAxonFramework5ProjectWithDevelopers(false);

        // When
        handleInstanceCommand(new AssignDeveloperAsLeadDeveloper("project-id", "steven.van.beelen@axoniq.io"));

        // Then
        assertThat(publishedEvents).containsExactly(
                new LeadDeveloperAssigned("project-id", "steven.van.beelen@axoniq.io"));

        assertThat(modelState.getLeadDeveloper().email()).isEqualTo("steven.van.beelen@axoniq.io");
        assertThat(modelState.getOtherDevelopers()).extracting("email")
                                                   .doesNotContain("steven.van.beelen@axoniq.io");
    }

    /**
     * Tests that changing the GitHub username of the lead developer works correctly. In other words, it tests whether
     * it chooses the matching field out of the two in the parent class.
     */
    @Test
    void canChangeGithubUsernameOfRegularDeveloper() {
        // Given an existing open source project with developers
        setupAxonFramework5ProjectWithDevelopers(true);

        // When
        handleInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                "mitchell.herrijgers@axoniq.io",
                                                                "CodeDrivenMitch-two"));

        // Then
        assertThat(publishedEvents).containsExactly(
                new DeveloperGithubUsernameChanged("project-id",
                                                   "mitchell.herrijgers@axoniq.io",
                                                   "CodeDrivenMitch",
                                                   "CodeDrivenMitch-two"));

        Developer developer = modelState.getOtherDevelopers()
                                        .stream()
                                        .filter(c -> c.email().equals("mitchell.herrijgers@axoniq.io"))
                                        .findAny()
                                        .orElseThrow();
        assertThat(developer.githubUsername()).isEqualTo("CodeDrivenMitch-two");
    }

    /**
     * Tests that changing the GitHub username of the lead developer works correctly. In other words, it tests whether
     * it chooses the matching field out of the two in the parent class.
     */
    @Test
    void canChangeGithubUsernameOfLeadDeveloper() {
        // Given an existing open source project with developers
        setupAxonFramework5ProjectWithDevelopers(true);

        // When
        handleInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                "steven.van.beelen@axoniq.io",
                                                                "smcvb-two"));

        // Then
        assertThat(publishedEvents).containsExactly(
                new DeveloperGithubUsernameChanged("project-id",
                                                   "steven.van.beelen@axoniq.io",
                                                   "smcvb",
                                                   "smcvb-two"));

        Developer leadDeveloper = modelState.getLeadDeveloper();
        assertThat(leadDeveloper.githubUsername()).isEqualTo("smcvb-two");
    }

    @Test
    void failsIfMultipleDevelopersOfSameEmailExistWithingSameCollection() {
        // In this case, we need to set wrong state to the command goes wrong
        modelState = new OpenSourceProject("project-id", "Axon Framework 5");
        modelState.getOtherDevelopers().add(new Developer("mitchell.herrijgers@axoniq.io", "CodeDrivenMitch"));
        modelState.getOtherDevelopers().add(new Developer("mitchell.herrijgers@axoniq.io", "CodeDrivenMitch"));

        // When & Then
        assertThatExceptionOfType(ChildAmbiguityException.class)
                .isThrownBy(() -> handleInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                                          "mitchell.herrijgers@axoniq.io",
                                                                                            "CodeDrivenMitch-two")));
    }
    @Test
    void failsIfMultipleDevelopersOfSameEmailExistWithinBothMembers() {
        // In this case, we need to set wrong state to the command goes wrong
        modelState = new OpenSourceProject("project-id", "Axon Framework 5");
        modelState.setLeadDeveloper(new Developer("steven.van.beelen@axoniq.io", "smcvb"));
        modelState.getOtherDevelopers().add(new Developer("steven.van.beelen@axoniq.io", "smcvb"));

        // When & Then
        assertThatExceptionOfType(ChildAmbiguityException.class)
                .isThrownBy(() -> handleInstanceCommand(new ChangeDeveloperGithubUsername("project-id",
                                                                                          "steven.van.beelen@axoniq.io",
                                                                                          "smcvb-two")));
    }

    /**
     * Sets up a base state for the Axon Framework 5 project, with several developers assigned to it.
     */
    private void setupAxonFramework5ProjectWithDevelopers(boolean assignLeadDeveloper) {
        modelState = new OpenSourceProject("project-id", "Axon Framework 5");
        handleInstanceCommand(new AssignDeveloperToProject("project-id", "steven.van.beelen@axoniq.io", "smcvb"));
        handleInstanceCommand(new AssignDeveloperToProject("project-id",
                                                           "mitchell.herrijgers@axoniq.io",
                                                           "CodeDrivenMitch"));
        handleInstanceCommand(new AssignDeveloperToProject("project-id", "mateusz.nowak@axoniq.io", "MateuszNaKodach"));
        handleInstanceCommand(new AssignDeveloperToProject("project-id", "allard@axoniq.io", "abuijze"));
        if (assignLeadDeveloper) {
            handleInstanceCommand(new AssignDeveloperAsLeadDeveloper("project-id", "steven.van.beelen@axoniq.io"));
        }
        publishedEvents.clear();
    }
}