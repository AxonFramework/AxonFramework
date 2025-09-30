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

import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.utils.StubLifecycleRegistry;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link QueryHandlingModule}.
 *
 * @author Steven van Beelen
 */
class SimpleQueryHandlingModuleTest {

    private static final QualifiedName QUERY_NAME = new QualifiedName(String.class);
    private static final QualifiedName RESPONSE_NAME = new QualifiedName(String.class);
    private static final QueryHandlerName QUERY_HANDLER_NAME = new QueryHandlerName(QUERY_NAME, RESPONSE_NAME);

    private QueryHandlingModule.SetupPhase setupPhase;
    private QueryHandlingModule.QueryHandlerPhase queryHandlerPhase;

    @BeforeEach
    void setUp() {
        setupPhase = QueryHandlingModule.named("test-subject");
        queryHandlerPhase = setupPhase.queryHandlers();
    }

    @Test
    void nameReturnsModuleName() {
        assertEquals("test-subject", setupPhase.queryHandlers().build().name());
    }

    @Test
    void buildRegistersQueryHandlers() {
        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        // This ensures we keep the SimpleQueryBus, from which we can retrieve the subscription for validation.
        AxonConfiguration configuration = MessagingConfigurer
                .create()
                .componentRegistry(cr -> cr.registerComponent(
                        CorrelationDataProviderRegistry.class, c -> new DefaultCorrelationDataProviderRegistry()
                ))
                .componentRegistry(cr -> cr.registerModule(
                        setupPhase.queryHandlers()
                                  .queryHandler(
                                          QUERY_NAME,
                                          RESPONSE_NAME,
                                          (query, context) -> MessageStream.just(null)
                                  )
                                  .build()
                ))
                .start();

        Configuration resultConfig = configuration.getModuleConfiguration("test-subject").orElseThrow();

        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        resultConfig.getComponent(QueryBus.class).describeTo(descriptor);

        Map<QueryHandlerName, QueryHandlingComponent> subscriptions = descriptor.getProperty("subscriptions");
        assertTrue(subscriptions.containsKey(QUERY_HANDLER_NAME));
    }

    @Test
    void buildAnnotatedQueryHandlingComponentSucceedsAndRegisters() {
        //noinspection unused
        var myQueryHandlingObject = new Object() {
            @org.axonframework.queryhandling.annotations.QueryHandler
            public String handle(String query) {
                return query;
            }
        };

        Configuration resultConfig =
                setupPhase.queryHandlers()
                          .annotatedQueryHandlingComponent(c -> myQueryHandlingObject)
                          .build()
                          .build(MessagingConfigurer.create().build(), new StubLifecycleRegistry());

        Optional<QueryHandlingComponent> optionalHandlingComponent = resultConfig.getOptionalComponent(
                QueryHandlingComponent.class, "QueryHandlingComponent[test-subject]");
        assertTrue(optionalHandlingComponent.isPresent());
        assertTrue(optionalHandlingComponent.get().supportedQueries()
                                            .contains(QUERY_HANDLER_NAME));
    }

    @Test
    void buildMessagingConfigurationSucceedsAndRegistersTheModuleWithComponent() {
        //noinspection unused
        var myQueryHandlingObject = new Object() {
            @org.axonframework.queryhandling.annotations.QueryHandler()
            public String handle(String query) {
                return query;
            }
        };

        Configuration resultConfig =
                MessagingConfigurer.create()
                                   .registerQueryHandlingModule(
                                           setupPhase.queryHandlers()
                                                     .annotatedQueryHandlingComponent(c -> myQueryHandlingObject)
                                                     .build()
                                   ).build();


        Optional<QueryHandlingComponent> optionalHandlingComponent = resultConfig
                .getModuleConfiguration("test-subject")
                .flatMap(m -> m.getOptionalComponent(
                        QueryHandlingComponent.class, "QueryHandlingComponent[test-subject]"
                ));
        assertTrue(optionalHandlingComponent.isPresent());
        assertTrue(optionalHandlingComponent.get().supportedQueries().contains(QUERY_HANDLER_NAME));
    }

    @Test
    void namedThrowsNullPointerExceptionForNullModuleName() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> QueryHandlingModule.named(null));
    }

    @Test
    void namedThrowsIllegalArgumentExceptionForEmptyModuleName() {
        assertThrows(IllegalArgumentException.class, () -> QueryHandlingModule.named(""));
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullQueryName() {
        //noinspection DataFlowIssue
        assertThrows(
                NullPointerException.class,
                () -> queryHandlerPhase.queryHandler(null, RESPONSE_NAME, (query, context) -> MessageStream.just(null))
        );
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullResponseName() {
        //noinspection DataFlowIssue
        assertThrows(
                NullPointerException.class,
                () -> queryHandlerPhase.queryHandler(QUERY_NAME, null, (query, context) -> MessageStream.just(null))
        );
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullQueryHandler() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> queryHandlerPhase.queryHandler(QUERY_NAME, RESPONSE_NAME, (QueryHandler) null));
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullQueryNameWithQueryHandler() {
        //noinspection DataFlowIssue
        assertThrows(
                NullPointerException.class,
                () -> queryHandlerPhase.queryHandler(null, RESPONSE_NAME, (query, context) -> MessageStream.just(null))
        );
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullQueryResponseWithQueryHandler() {
        //noinspection DataFlowIssue
        assertThrows(
                NullPointerException.class,
                () -> queryHandlerPhase.queryHandler(QUERY_NAME, null, (query, context) -> MessageStream.just(null))
        );
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullQueryNameWithQueryHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> queryHandlerPhase.queryHandler(
                null, RESPONSE_NAME, c -> (query, context) -> null
        ));
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullResponseNameWithQueryHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> queryHandlerPhase.queryHandler(
                QUERY_NAME, null, c -> (query, context) -> null
        ));
    }

    @Test
    void queryHandlerThrowsNullPointerExceptionForNullQueryHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> queryHandlerPhase.queryHandler(
                QUERY_NAME, RESPONSE_NAME, (ComponentBuilder<QueryHandler>) null
        ));
    }

    @Test
    void queryHandlingComponentThrowsNullPointerExceptionForNullQueryHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> queryHandlerPhase.queryHandlingComponent(null));
    }

    @Test
    void annotatedQueryHandlingComponentThrowsNullPointerExceptionForNullQueryHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> queryHandlerPhase.annotatedQueryHandlingComponent(null));
    }

    @Test
    void commandHandlingThrowsNullPointerExceptionForNullQueryHandlerPhaseConsumer() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> setupPhase.queryHandlers(null));
    }
}