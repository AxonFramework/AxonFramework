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

package org.axonframework.queryhandling;

import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.junit.jupiter.api.*;

/**
 * Test class validating the {@link SimpleQueryUpdateEmitter}.
 *
 * @author Allard Buijze
 * @author Corrado Musumeci
 * @author Steven van Beelen
 */
class SimpleQueryUpdateEmitterTest {

    private QueryBus queryBus = QueryBusTestUtils.aQueryBus();
    private SimpleQueryUpdateEmitter testSubject;

    @BeforeEach
    void setUp() {
        queryBus = QueryBusTestUtils.aQueryBus();
        testSubject = new SimpleQueryUpdateEmitter(new ClassBasedMessageTypeResolver(), queryBus, null);
    }

    @Nested
    class Construction {

        @Test
        void throwsNullPointerExceptionForNullMessageTypeResolver() {

        }

        @Test
        void throwsNullPointerExceptionForNullQueryBus() {

        }

        @Test
        void throwsNullPointerExceptionForNullApplicationContext() {

        }
    }

    @Nested
    class EmittingUpdates {

        @Test
        void emitForQueryType() {

        }

        @Test
        void emitForQueryTypeAndFilter() {

        }

        @Test
        void emitForQueryTypeThrowsMessageTypeNotResolvedException() {

        }

        @Test
        void emitForQueryTypeThrowsConversionException() {

        }

        @Test
        void emitForQueryName() {

        }

        @Test
        void emitForQueryNameAndGivenFilter() {

        }
    }

    @Nested
    class Complete {

        @Test
        void completeForQueryType() {

        }

        @Test
        void completeForQueryTypeAndFilter() {

        }

        @Test
        void completeForQueryTypeThrowsMessageTypeNotResolvedException() {

        }

        @Test
        void completeForQueryTypeThrowsConversionException() {

        }

        @Test
        void completeForQueryName() {

        }

        @Test
        void completeForQueryNameAndGivenFilter() {

        }
    }

    @Nested
    class CompleteExceptionally {

        @Test
        void completeExceptionallyForQueryType() {

        }

        @Test
        void completeExceptionallyForQueryTypeAndFilter() {

        }

        @Test
        void completeExceptionallyForQueryTypeThrowsMessageTypeNotResolvedException() {

        }

        @Test
        void completeExceptionallyForQueryTypeThrowsConversionException() {

        }

        @Test
        void completeExceptionallyForQueryName() {

        }

        @Test
        void completeExceptionallyForQueryNameAndGivenFilter() {

        }
    }
}
