n/*
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

/**
 * Pseudocode demonstrating CompletableFuture equivalents to Spring @Async annotation
 *
 * This example shows how @Async methods can be manually implemented using CompletableFuture
 * for asynchronous execution patterns.
 */
public class AsyncEquivalentExample {

    // ==========================================
    // SPRING @ASYNC VERSION
    // ==========================================

    @Async
    public void processEventAsync(Event event) {
        // This runs in a separate thread managed by Spring's TaskExecutor
        performLongRunningOperation(event);
        logCompletion(event);
    }

    @Async
    public Future<String> processWithResultAsync(Event event) {
        // Spring wraps the return value in a Future
        String result = performLongRunningOperation(event);
        return AsyncResult.forValue(result);
    }

    @Async("customExecutor")
    public CompletableFuture<ProcessingResult> processWithCustomExecutor(Event event) {
        // Uses specific executor named "customExecutor"
        ProcessingResult result = complexProcessing(event);
        return CompletableFuture.completedFuture(result);
    }

    // ==========================================
    // COMPLETABLEFUTURE EQUIVALENT
    // ==========================================

    private final ExecutorService defaultExecutor = Executors.newCachedThreadPool();
    private final ExecutorService customExecutor = Executors.newFixedThreadPool(5);

    // Equivalent to @Async void method
    public CompletableFuture<Void> processEventAsyncManual(Event event) {
        return CompletableFuture.runAsync(() -> {
            performLongRunningOperation(event);
            logCompletion(event);
        }, defaultExecutor);
    }

    // Equivalent to @Async method returning Future<String>
    public CompletableFuture<String> processWithResultAsyncManual(Event event) {
        return CompletableFuture.supplyAsync(() -> {
            return performLongRunningOperation(event);
        }, defaultExecutor);
    }

    // Equivalent to @Async with custom executor
    public CompletableFuture<ProcessingResult> processWithCustomExecutorManual(Event event) {
        return CompletableFuture.supplyAsync(() -> {
            return complexProcessing(event);
        }, customExecutor);
    }

    // ==========================================
    // USAGE PATTERNS
    // ==========================================

    public void demonstrateUsagePatterns() {
        Event event = new Event("test-event");

        // Fire-and-forget pattern (void async methods)
        processEventAsyncManual(event)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    handleAsyncError(throwable);
                }
                // No result to process for void methods
            });

        // Get result pattern
        processWithResultAsyncManual(event)
            .thenAccept(result -> {
                System.out.println("Processing completed with result: " + result);
            })
            .exceptionally(throwable -> {
                handleAsyncError(throwable);
                return null;
            });

        // Chaining async operations
        processWithResultAsyncManual(event)
            .thenCompose(result -> processWithCustomExecutorManual(event))
            .thenAccept(finalResult -> {
                System.out.println("Final result: " + finalResult);
            });

        // Combining multiple async operations
        CompletableFuture<String> operation1 = processWithResultAsyncManual(event);
        CompletableFuture<ProcessingResult> operation2 = processWithCustomExecutorManual(event);

        CompletableFuture.allOf(operation1, operation2)
            .thenRun(() -> {
                System.out.println("Both operations completed");
            });
    }

    // ==========================================
    // AXON FRAMEWORK CONTEXT EXAMPLE
    // ==========================================

    /**
     * Example showing how this might be applied in AxonFramework context
     * for event handling with sequencing
     */
    public class SequencedEventHandlingExample {

        private final Map<String, CompletableFuture<Void>> sequenceExecutions =
            new ConcurrentHashMap<>();

        // Spring @Async equivalent for sequenced event handling
        public CompletableFuture<Void> handleEventWithSequencing(Event event, String sequenceId) {

            // Get or create a sequence chain for this sequence ID
            CompletableFuture<Void> previousExecution = sequenceExecutions.get(sequenceId);

            CompletableFuture<Void> newExecution;
            if (previousExecution == null) {
                // First event in sequence - execute immediately
                newExecution = CompletableFuture.runAsync(() -> {
                    processEvent(event);
                }, defaultExecutor);
            } else {
                // Chain after previous event in same sequence
                newExecution = previousExecution.thenRunAsync(() -> {
                    processEvent(event);
                }, defaultExecutor);
            }

            // Update the sequence chain
            sequenceExecutions.put(sequenceId, newExecution);

            // Clean up completed chains to prevent memory leaks
            newExecution.whenComplete((result, throwable) -> {
                if (newExecution.isDone()) {
                    sequenceExecutions.remove(sequenceId, newExecution);
                }
            });

            return newExecution;
        }

        // Events with different sequence IDs can run concurrently
        public void handleMultipleEvents() {
            Event event1 = new Event("event1");
            Event event2 = new Event("event2");
            Event event3 = new Event("event3");

            // These will run sequentially within each sequence
            CompletableFuture<Void> seq1_1 = handleEventWithSequencing(event1, "sequence-A");
            CompletableFuture<Void> seq1_2 = handleEventWithSequencing(event2, "sequence-A");

            // This runs concurrently with sequence-A events
            CompletableFuture<Void> seq2_1 = handleEventWithSequencing(event3, "sequence-B");

            // Wait for all to complete
            CompletableFuture.allOf(seq1_2, seq2_1).join();
        }
    }

    // ==========================================
    // HELPER METHODS (PSEUDOCODE)
    // ==========================================

    private String performLongRunningOperation(Event event) {
        // Simulate long-running operation
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Processed: " + event.getId();
    }

    private ProcessingResult complexProcessing(Event event) {
        // Simulate complex processing
        return new ProcessingResult(event.getId(), "completed");
    }

    private void processEvent(Event event) {
        System.out.println("Processing event: " + event.getId());
    }

    private void logCompletion(Event event) {
        System.out.println("Completed processing: " + event.getId());
    }

    private void handleAsyncError(Throwable throwable) {
        System.err.println("Async operation failed: " + throwable.getMessage());
    }

    // Helper classes for pseudocode
    private static class Event {
        private final String id;

        public Event(String id) { this.id = id; }
        public String getId() { return id; }
        public Object getPayload() { return this; }
    }

    private static class ProcessingResult {
        private final String eventId;
        private final String status;

        public ProcessingResult(String eventId, String status) {
            this.eventId = eventId;
            this.status = status;
        }

        @Override
        public String toString() {
            return "ProcessingResult{eventId='" + eventId + "', status='" + status + "'}";
        }
    }
}
