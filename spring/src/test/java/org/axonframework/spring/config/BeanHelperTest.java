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

package org.axonframework.spring.config;

import org.axonframework.modelling.command.LegacyRepository;
import org.junit.jupiter.api.*;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// TODO #3499 Fix/remove as part of referred to issue
class BeanHelperTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void verifyRetrievesRepositoryFromConfiguration() {
//        LegacyConfiguration configuration = mock(LegacyConfiguration.class);
//        LegacyRepository mockRepository = mock(LegacyRepository.class);
//        when(configuration.repository(any())).thenReturn(mockRepository);
//
//        LegacyRepository<?> actual = BeanHelper.repository(Random.class, configuration);
//
//        verify(configuration).repository(Random.class);
//        assertSame(mockRepository, actual);
    }
}