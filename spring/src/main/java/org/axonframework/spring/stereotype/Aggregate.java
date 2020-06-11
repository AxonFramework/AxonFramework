/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.spring.stereotype;

import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that informs Axon's auto configurer for Spring that a given {@link Component} is an aggregate instance.
 *
 * @author Allard Buijze
 * @since 3.0
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
@Scope("prototype")
@AggregateRoot
public @interface Aggregate {

    /**
     * Selects the name of the AggregateRepository bean. If left empty a new repository is created. In that case the
     * name of the repository will be based on the simple name of the aggregate's class.
     */
    String repository() default "";

    /**
     * Sets the name of the bean providing the snapshot trigger definition. If none is provided, no snapshots are
     * created, unless explicitly configured on the referenced repository.
     * <p>
     * Note that the use of {@link #repository()}, or provisioning a
     * {@link org.axonframework.modelling.command.Repository} to the Spring context using the default naming scheme
     * overrides this setting, as a Repository explicitly defines the snapshot trigger definition. The default name
     * corresponds to {@code "[aggregate-name]Repository"}, thus a {@code Trade} Aggregate would by default create/look
     * for a bean named {@code "tradeRepository"}.
     */
    String snapshotTriggerDefinition() default "";

    /**
     * Sets the name of the bean providing the {@link org.axonframework.eventsourcing.snapshotting.SnapshotFilter}. If
     * none is provided, all snapshots will be taken into account unless explicitly configured on the event store.
     */
    String snapshotFilter() default "";

    /**
     * Get the String representation of the aggregate's type. Optional. This defaults to the simple name of the
     * annotated class.
     */
    String type() default "";

    /**
     * Selects the name of the {@link CommandTargetResolver} bean. If left empty,
     * {@link CommandTargetResolver} bean from application context will be used. If
     * the bean is not defined in the application context, {@link AnnotationCommandTargetResolver}
     * will be used.
     */
    String commandTargetResolver() default "";

    /**
     * Sets whether or not to filter events by Aggregate type. This is used to support installations where multiple
     * Aggregate types can have overlapping Aggregate identifiers. This is only meaningful for event-sourced
     * Aggregates.
     */
    boolean filterEventsByType() default false;

    /**
     * Sets the name of the bean providing the caching. If none is provided, no cache is
     * created, unless explicitly configured on the referenced repository.
     */
    String cache() default "";
}
