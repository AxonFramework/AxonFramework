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

package org.axonframework.queryhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Module} and {@link ModuleBuilder} implementation providing operation to construct a query handling
 * application module.
 * <p>
 * The {@code QueryHandlingModule} follows a builder paradigm, wherein several {@link QueryHandler QueryHandlers} can be
 * registered in either order.
 * <p>
 * To register query handlers, a similar registration phase switch should be made, by invoking
 * {@link SetupPhase#queryHandlers()}.
 * <p>
 * Here's an example of how to register two query handler lambdas:
 * <pre>
 * QueryHandlingModule.named("my-module")
 *                              .queryHandlers()
 *                              .queryHandler(new QualifiedName(FetchClasses.class),
 *                                            new QualifiedName(Classes.class),
 *                                            (query, context) -> { ...query handling logic... })
 *                              .queryHandler(new QualifiedName(FindStudentForName.class),
 *                                            new QualifiedName(Student.class),
 *                                            (query, context) -> { ...query handling logic... });
 * </pre>
 * <p>
 * Note that users do not have to invoke {@link #build()} themselves when using this interface, as the
 * {@link org.axonframework.configuration.ApplicationConfigurer} takes care of that.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface QueryHandlingModule extends Module, ModuleBuilder<QueryHandlingModule> {

    /**
     * Starts a {@code QueryHandlingModule} module with the given {@code moduleName}.
     *
     * @param moduleName The name of the {@code QueryHandlingModule} under construction.
     * @return The setup phase of this module, for a fluent API.
     */
    static QueryHandlingModule.SetupPhase named(@Nonnull String moduleName) {
        return new SimpleQueryHandlingModule(moduleName);
    }

    /**
     * The setup phase of the query handling module.
     * <p>
     * The {@link #queryHandlers()} method allows users to start configuring all the {@link QueryHandler QueryHandlers}
     * for this module.
     */
    interface SetupPhase {

        /**
         * Initiates the query handler configuration phase for this module.
         *
         * @return The query handler phase of this module, for a fluent API.
         */
        QueryHandlingModule.QueryHandlerPhase queryHandlers();

        /**
         * Initiates the query handler configuration phase for this module, as well as performing the given
         * {@code configurationLambda} within this phase.
         *
         * @param configurationLambda A consumer of the query handler phase, performing query handler configuration
         *                            right away.
         * @return The query handler phase of this module, for a fluent API.
         */
        default QueryHandlingModule.QueryHandlerPhase queryHandlers(
                @Nonnull Consumer<QueryHandlingModule.QueryHandlerPhase> configurationLambda
        ) {
            QueryHandlingModule.QueryHandlerPhase queryHandlerPhase = queryHandlers();
            configurationLambda.accept(queryHandlerPhase);
            return queryHandlerPhase;
        }
    }

    /**
     * The query handler configuration phase of the query handling module.
     * <p>
     * Every registered {@link QueryHandler} will be subscribed with the
     * {@link org.axonframework.queryhandling.QueryBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer} this module is given to.
     * <p>
     * Provides roughly two options for configuring query handlers. Firstly, a query handler can be registered as is,
     * through the {@link #queryHandler(QualifiedName, QualifiedName, QueryHandler)} method. Secondly, if the query
     * handler provides components from the {@link Configuration}, a {@link ComponentBuilder builder} of the query
     * handler can be registered through the {@link #queryHandler(QualifiedName, QualifiedName, ComponentBuilder)}
     * method.
     */
    interface QueryHandlerPhase extends ModuleBuilder<QueryHandlingModule> {

        /**
         * Registers the given {@code queryHandler} for the given qualified {@code queryName} and {@code responseName}
         * within this module.
         * <p>
         * Use this query handler registration method when the query handler in question does not require entities or
         * receives entities through another mechanism.
         * <p>
         * Once this module is finalized, the query handler will be subscribed with the
         * {@link org.axonframework.queryhandling.QueryBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param queryName    The qualified name of the query the given {@code queryHandler} can handle.
         * @param responseName The qualified name of the query response the given {@code queryHandler} returns.
         * @param queryHandler The query handler to register with this module.
         * @return The query handler phase of this builder, for a fluent API.
         */
        default QueryHandlingModule.QueryHandlerPhase queryHandler(@Nonnull QualifiedName queryName,
                                                                   @Nonnull QualifiedName responseName,
                                                                   @Nonnull QueryHandler queryHandler) {
            requireNonNull(queryHandler, "The query handler cannot be null.");
            return queryHandler(queryName, responseName, c -> queryHandler);
        }

        /**
         * Registers the given {@code queryHandlerBuilder} for the given qualified {@code queryName} and
         * {@code responseName} within this module.
         * <p>
         * Once this module is finalized, the query handler from the {@code queryHandlerBuilder} will be subscribed with
         * the {@link org.axonframework.queryhandling.QueryBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param queryName           The qualified name of the query the {@link QueryHandler} created by the given
         *                            {@code queryHandlerBuilder}.
         * @param responseName        The qualified name of the query response the {@link QueryHandler} created by the
         *                            given {@code queryHandlerBuilder} returns.
         * @param queryHandlerBuilder A builder of a {@link QueryHandler}. Provides the {@link Configuration} to
         *                            retrieve components from to use during construction of the query handler.
         * @return The query handler phase of this builder, for a fluent API.
         */
        QueryHandlingModule.QueryHandlerPhase queryHandler(
                @Nonnull QualifiedName queryName,
                @Nonnull QualifiedName responseName,
                @Nonnull ComponentBuilder<QueryHandler> queryHandlerBuilder
        );

        /**
         * Registers the given {@code handlingComponentBuilder} within this module.
         * <p>
         * Use this query handler registration method when the query handling component in question does not require
         * entities or receives entities through another mechanism.
         * <p>
         * Once this module is finalized, the resulting {@link QueryHandlingComponent} from the
         * {@code handlingComponentBuilder} will be subscribed with the {@link org.axonframework.queryhandling.QueryBus}
         * of the {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param handlingComponentBuilder A builder of a {@link QueryHandlingComponent}. Provides the
         *                                 {@link Configuration} to retrieve components from to use during construction
         *                                 of the query handling component.
         * @return The query handler phase of this builder, for a fluent API.
         */
        QueryHandlingModule.QueryHandlerPhase queryHandlingComponent(
                @Nonnull ComponentBuilder<QueryHandlingComponent> handlingComponentBuilder
        );

        /**
         * Registers the given {@code handlingComponentBuilder} as an {@link AnnotatedQueryHandlingComponent} within
         * this module.
         * <p>
         * This will scan the given {@code handlingComponentBuilder} for methods annotated with {@link QueryHandler} and
         * register them as query handlers for the {@link org.axonframework.queryhandling.QueryBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer}.
         *
         * @param handlingComponentBuilder A builder of a {@link QueryHandlingComponent}. Provides the
         *                                 {@link Configuration} to retrieve components from to use during construction
         *                                 of the query handling component.
         * @return The query handler phase of this builder, for a fluent API.
         */
        default QueryHandlingModule.QueryHandlerPhase annotatedQueryHandlingComponent(
                @Nonnull ComponentBuilder<Object> handlingComponentBuilder
        ) {
            requireNonNull(handlingComponentBuilder, "The handling component builder cannot be null.");
            return queryHandlingComponent(c -> new AnnotatedQueryHandlingComponent<>(
                    handlingComponentBuilder.build(c),
                    c.getComponent(ParameterResolverFactory.class),
                    ClasspathHandlerDefinition.forClass(c.getClass()),
                    c.getComponent(MessageTypeResolver.class),
                    c.getComponent(MessageConverter.class)
            ));
        }
    }
}
