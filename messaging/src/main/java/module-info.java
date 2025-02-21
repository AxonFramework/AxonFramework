module axon.messaging {
    requires cache.api;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires db.scheduler;
    requires ehcache;
    requires jakarta.annotation;
    requires jakarta.persistence;
    requires jakarta.validation;
    requires java.desktop;
    requires java.management;
    requires java.naming;
    requires java.sql;
    requires jsr305;
    requires nu.xom;
    requires org.dom4j;
    requires org.jobrunr.core;
    requires org.reactivestreams;
    requires org.slf4j;
    requires quartz;
    requires reactor.core;
    requires xstream;

    exports org.axonframework.common;
    exports org.axonframework.commandhandling;
    exports org.axonframework.deadline;
    exports org.axonframework.eventhandling;
    exports org.axonframework.eventhandling.scheduling.jobrunr to com.fasterxml.jackson.databind;
    exports org.axonframework.lifecycle;
    exports org.axonframework.messaging;
    exports org.axonframework.monitoring;
    exports org.axonframework.queryhandling;
    exports org.axonframework.serialization;
    exports org.axonframework.tracing;

    provides org.axonframework.common.property.PropertyAccessStrategy with
            org.axonframework.common.property.BeanPropertyAccessStrategy,
            org.axonframework.common.property.DirectPropertyAccessStrategy,
            org.axonframework.common.property.UniformPropertyAccessStrategy;
    provides org.axonframework.serialization.ContentTypeConverter with
            org.axonframework.serialization.converters.ByteArrayToInputStreamConverter,
            org.axonframework.serialization.converters.BlobToInputStreamConverter,
            org.axonframework.serialization.converters.InputStreamToByteArrayConverter,
            org.axonframework.serialization.converters.ByteArrayToStringConverter,
            org.axonframework.serialization.converters.StringToByteArrayConverter;
    provides org.axonframework.messaging.annotation.HandlerDefinition with
            org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
    provides org.axonframework.messaging.annotation.HandlerEnhancerDefinition with
            org.axonframework.commandhandling.annotation.MethodCommandHandlerDefinition,
            org.axonframework.deadline.annotation.DeadlineMethodMessageHandlerDefinition,
            org.axonframework.eventhandling.replay.ReplayAwareMessageHandlerWrapper,
            org.axonframework.messaging.annotation.MessageHandlerInterceptorDefinition,
            org.axonframework.queryhandling.annotation.MethodQueryMessageHandlerDefinition;
    provides org.axonframework.messaging.annotation.ParameterResolverFactory with
            org.axonframework.commandhandling.annotation.CurrentUnitOfWorkParameterResolverFactory,
            org.axonframework.messaging.annotation.InterceptorChainParameterResolverFactory,
            org.axonframework.eventhandling.ConcludesBatchParameterResolverFactory,
            org.axonframework.eventhandling.SequenceNumberParameterResolverFactory,
            org.axonframework.eventhandling.TrackingTokenParameterResolverFactory,
            org.axonframework.eventhandling.TimestampParameterResolverFactory,
            org.axonframework.eventhandling.replay.ReplayParameterResolverFactory,
            org.axonframework.eventhandling.replay.ReplayContextParameterResolverFactory,
            org.axonframework.messaging.annotation.DefaultParameterResolverFactory,
            org.axonframework.messaging.annotation.MessageIdentifierParameterResolverFactory,
            org.axonframework.messaging.annotation.SourceIdParameterResolverFactory,
            org.axonframework.messaging.annotation.ResultParameterResolverFactory,
            org.axonframework.messaging.annotation.ScopeDescriptorParameterResolverFactory,
            org.axonframework.messaging.annotation.AggregateTypeParameterResolverFactory,
            org.axonframework.messaging.deadletter.DeadLetterParameterResolverFactory;

    uses org.axonframework.common.IdentifierFactory;
    uses org.axonframework.common.property.PropertyAccessStrategy;
    uses org.axonframework.messaging.annotation.ParameterResolverFactory;
    uses org.axonframework.messaging.annotation.HandlerDefinition;
    uses org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
    uses org.axonframework.serialization.ContentTypeConverter;

    opens org.axonframework.deadline to xstream;

}