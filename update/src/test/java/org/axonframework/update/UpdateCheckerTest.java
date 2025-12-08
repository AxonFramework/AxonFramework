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

package org.axonframework.update;

import org.axonframework.update.api.UpdateCheckResponse;
import org.axonframework.update.configuration.UsagePropertyProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("slow")
class UpdateCheckerTest {

    @Mock
    private UpdateCheckerHttpClient httpClient;

    @Mock
    private UpdateCheckerReporter reporter;

    @Mock
    private UsagePropertyProvider usagePropertyProvider;

    private UpdateChecker updateChecker;

    @BeforeEach
    void setUp() {
        when(usagePropertyProvider.getDisabled()).thenReturn(false);
        updateChecker = new UpdateChecker(httpClient, reporter, usagePropertyProvider);
    }

    @AfterEach
    void tearDown() {
        updateChecker.stop();
    }

    @Test
    void shouldExecuteRepeatedly() {
        // Given
        UpdateCheckResponse response1 = new UpdateCheckResponse(1, List.of(), List.of());
        UpdateCheckResponse response2 = new UpdateCheckResponse(2, List.of(), List.of());

        // When
        when(httpClient.sendRequest(any(), eq(true)))
                .thenReturn(Optional.of(response1));
        when(httpClient.sendRequest(any(), eq(false)))
                .thenReturn(Optional.of(response2));

        // First call on start()
        updateChecker.start();

        // Then
        // Wait for first report
        await().atMost(Duration.ofSeconds(2))
               .untilAsserted(() -> verify(reporter, times(1)).report(any(), eq(response1)));

        // Wait for second report
        await().atMost(Duration.ofSeconds(3))
               .untilAsserted(() -> verify(reporter, times(1)).report(any(), eq(response2)));

        // Cleanup
        updateChecker.stop();
    }

    @Test
    void shouldHandleErrorsWithExponentialBackoff() {
        // Given
        UpdateCheckResponse successResponse = new UpdateCheckResponse(1, List.of(), List.of());
        AtomicInteger requestCount = new AtomicInteger();

        // When
        when(httpClient.sendRequest(any(), anyBoolean())).thenAnswer(invocation -> {
            int count = requestCount.incrementAndGet();
            if (count < 4) {
                return Optional.empty(); // First three calls - error
            } else {
                return Optional.of(successResponse); // Fourth call - success
            }
        });

        // Start the checker
        updateChecker.start();

        // Then
        // Verify that we eventually get 3 HTTP requests with exponential backoff
        await().atMost(Duration.ofSeconds(30))
               .untilAsserted(() -> verify(httpClient, times(3)).sendRequest(any(), anyBoolean()));

        // Cleanup
        updateChecker.stop();
    }

    @Test
    void shouldHandleExceptionsGracefully() {
        // Given
        UpdateCheckResponse successResponse = new UpdateCheckResponse(1, List.of(), List.of());
        AtomicInteger requestCount = new AtomicInteger();

        // When
        when(httpClient.sendRequest(any(), anyBoolean())).thenAnswer(invocation -> {
            int count = requestCount.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("Test exception");
            } else {
                return Optional.of(successResponse);
            }
        });

        // Start the checker
        updateChecker.start();

        // Then
        // Verify that we eventually get 2 HTTP requests (first throws exception, second succeeds)
        await().atMost(Duration.ofSeconds(6))
               .untilAsserted(() -> verify(httpClient, times(2)).sendRequest(any(), anyBoolean()));

        // Verify that we get a report after the second call
        await().atMost(Duration.ofSeconds(2))
               .untilAsserted(() -> verify(reporter, times(1)).report(any(), any()));

        // Cleanup
        updateChecker.stop();
    }

    @Test
    void shouldNotStartWhenDisabled() {
        // Given
        when(usagePropertyProvider.getDisabled()).thenReturn(true);
        UpdateChecker disabledChecker = new UpdateChecker(httpClient, reporter, usagePropertyProvider);

        // When
        disabledChecker.start();

        // Then
        // Wait a bit to ensure no requests are made
        await().during(Duration.ofMillis(500))
               .atMost(Duration.ofSeconds(1))
               .untilAsserted(() -> verify(httpClient, never()).sendRequest(any(), anyBoolean()));

        // Cleanup
        disabledChecker.stop();
    }
}