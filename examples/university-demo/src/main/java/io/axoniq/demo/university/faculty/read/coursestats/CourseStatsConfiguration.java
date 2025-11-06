package io.axoniq.demo.university.faculty.read.coursestats;

import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessorModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;

public class CourseStatsConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        PooledStreamingEventProcessorModule projectionProcessor = EventProcessorModule
                .pooledStreaming("Projection_CourseStats_Processor")
                .eventHandlingComponents(
                        c -> c.annotated(cfg -> new CoursesStatsProjection(cfg.getComponent(CourseStatsRepository.class)))
                ).notCustomized();

        QueryHandlingModule getCourseStatsByIdQueryHandler = QueryHandlingModule.named("get-course-stats-by-id")
                .queryHandlers()
                .annotatedQueryHandlingComponent(cfg -> new GetCourseStatsByIdQueryHandler(cfg.getComponent(CourseStatsRepository.class)))
                .build();

        return configurer
                .componentRegistry(cr -> cr.registerComponent(CourseStatsRepository.class, cfg -> new InMemoryCourseStatsRepository()))
                .registerQueryHandlingModule(getCourseStatsByIdQueryHandler)
                .modelling(modelling -> modelling.messaging(messaging -> messaging.eventProcessing(eventProcessing ->
                        eventProcessing.pooledStreaming(ps -> ps.processor(projectionProcessor))
                )));
    }

    private CourseStatsConfiguration() {
        // Prevent instantiation
    }

}
