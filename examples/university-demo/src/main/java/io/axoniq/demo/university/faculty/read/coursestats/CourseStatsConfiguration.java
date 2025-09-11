package io.axoniq.demo.university.faculty.read.coursestats;

import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

import java.util.concurrent.CompletableFuture;

class CourseStatsConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        PooledStreamingEventProcessorModule projectionProcessor = EventProcessorModule
                .pooledStreaming("Projection_CourseStats_Processor")
                .eventHandlingComponents(
                        c -> c.annotated(cfg -> new CoursesStatsProjection(cfg.getComponent(CourseStatsRepository.class)))
                )
                // Due to a minor bug in the InMemoryEventStorageEngine this customization is needed if you want to use the implementation in the tests
                .customized((c, cus) -> cus.initialToken(s -> CompletableFuture.completedFuture(new GlobalSequenceTrackingToken(0))));

        return configurer
                .componentRegistry(cr -> cr.registerComponent(CourseStatsRepository.class, cfg -> new InMemoryCourseStatsRepository()))
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                        eventProcessing.pooledStreaming(ps -> ps.processor(projectionProcessor))
                )));
    }

    private CourseStatsConfiguration() {
        // Prevent instantiation
    }

}
