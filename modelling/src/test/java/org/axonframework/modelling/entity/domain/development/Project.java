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

package org.axonframework.modelling.entity.domain.development;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.entity.annotation.AnnotatedEntityMetamodel;
import org.axonframework.modelling.entity.annotation.EntityMember;
import org.axonframework.modelling.entity.domain.development.commands.AssignDeveloperAsLeadDeveloper;
import org.axonframework.modelling.entity.domain.development.commands.AssignDeveloperToProject;
import org.axonframework.modelling.entity.domain.development.commands.CreateProjectCommand;
import org.axonframework.modelling.entity.domain.development.commands.RenameProjectCommand;
import org.axonframework.modelling.entity.domain.development.common.ProjectType;
import org.axonframework.modelling.entity.domain.development.events.DeveloperAssignedToProject;
import org.axonframework.modelling.entity.domain.development.events.LeadDeveloperAssigned;
import org.axonframework.modelling.entity.domain.development.events.ProjectCreatedEvent;
import org.axonframework.modelling.entity.domain.development.events.ProjectRenamedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fictional domain model representing a project in a software development context. Used for validating the Axon
 * Framework's modelling capabilities, such as the
 * {@link AnnotatedEntityMetamodel}.
 * <p>
 * The model handles various commands that are validated based on state, and events that are published to reflect
 * changes in the project state. This model does use events to evolve its state, but does not use an event store to
 * source the model from earlier events. As such, it is a state-stored model rather than an event-sourced model.
 * <p>
 * It uses various features, as many as possible in fact, of the entity model, such as:
 * - Polymorphic entities
 * - Entity members in abstract entity
 * - Entity member in concrete entity
 * - Commands in abstract entity
 * - Commands in concrete entity
 * - Multiple EntityMember-annotated fields that use the same routing key
 * - Variations of routing key usage
 * - Evolving mutable child entities
 * - Evolving immutable child entities
 *
 * @since 5.0.0
 * @author Mitchell Herrijgers
 */
public abstract class Project {

    private final String projectId;
    private String name;

    protected Project(String projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    @EntityMember
    private Developer leadDeveloper;

    @EntityMember
    private List<Developer> otherDevelopers = new ArrayList<>();

    @EntityMember
    private List<Milestone> features = new ArrayList<>();

    @CommandHandler
    public static String handle(CreateProjectCommand command, EventAppender appender) {
        String identifier = IdentifierFactory.getInstance().generateIdentifier();
        if (command.projectType() == ProjectType.UNKNOWN) {
            throw new IllegalArgumentException("Project type must be known before creating a project.");
        }
        appender.append(new ProjectCreatedEvent(identifier, command.name(), command.projectType()));
        return identifier;
    }

    @CommandHandler
    public void handle(RenameProjectCommand command, EventAppender appender) {
        appender.append(
                new ProjectRenamedEvent(
                        command.projectId(),
                        command.name()
                )
        );
    }

    @CommandHandler
    public void handle(AssignDeveloperToProject command, EventAppender appender) {
        if (otherDevelopers.stream().anyMatch(developer -> developer.email().equals(command.email()))) {
            throw new IllegalArgumentException(
                    "Developer with email " + command.email() + " is already assigned to this project.");
        }
        if (leadDeveloper != null && leadDeveloper.email().equals(command.email())) {
            throw new IllegalArgumentException(
                    "Developer with email " + command.email() + " is already assigned to this project.");
        }
        appender.append(new DeveloperAssignedToProject(projectId, command.email(), command.githubUsername()));
    }

    @CommandHandler
    public void handle(AssignDeveloperAsLeadDeveloper command, EventAppender appender) {
        if(leadDeveloper != null){
            if(!leadDeveloper.email().equals(command.email())) {
                throw new IllegalArgumentException(
                        "Developer with email " + command.email() + " is already assigned as lead developer.");
            }
        }

        if(otherDevelopers.stream().noneMatch(developer -> developer.email().equals(command.email()))) {
            throw new IllegalArgumentException(
                    "Developer with email " + command.email() + " is not assigned to this project.");
        }

        appender.append(new LeadDeveloperAssigned(projectId, command.email()));
    }

    @EventHandler
    public void on(ProjectRenamedEvent event) {
        this.name = event.name();
    }

    @EventHandler
    public void on(DeveloperAssignedToProject event) {
        otherDevelopers.add(new Developer(event.email(), event.name()));
    }

    @EventHandler
    public void on(LeadDeveloperAssigned event) {
        this.leadDeveloper = otherDevelopers.stream()
                .filter(developer -> developer.email().equals(event.email()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Developer with email " + event.email() + " is not assigned to this project."));
        otherDevelopers.remove(leadDeveloper);
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public List<Developer> getOtherDevelopers() {
        return otherDevelopers;
    }

    public Developer getLeadDeveloper() {
        return leadDeveloper;
    }

    public void setLeadDeveloper(Developer leadDeveloper) {
        this.leadDeveloper = leadDeveloper;
    }
}
