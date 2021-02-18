package org.axonframework.messaging.annotation;

/**
 * Dictionary containing the possible attributes a {@link MessageHandlingMember} can have. Can be used as input for
 * {@link MessageHandlingMember#attribute(String)}.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public abstract class HandlerAttributeDictionary {

    /**
     * Attribute key referencing the {@link org.axonframework.messaging.Message} type being handled by the handler.
     */
    public static final String MESSAGE_TYPE = "MessageHandler.messageType";
    /**
     * Attribute key referencing the payload type contained in the {@link org.axonframework.messaging.Message}.
     */
    public static final String PAYLOAD_TYPE = "MessageHandler.payloadType";
    /**
     * Attribute key referencing the name of the {@link org.axonframework.commandhandling.CommandMessage} the handler
     * can handle.
     */
    public static final String COMMAND_NAME = "CommandHandler.commandName";
    /**
     * Attribute key referencing the routing key used to route a {@link org.axonframework.commandhandling.CommandMessage}
     * to the handler.
     */
    public static final String COMMAND_ROUTING_KEY = "CommandHandler.routingKey";
    /**
     * Attribute key referencing the name of the {@link org.axonframework.queryhandling.QueryMessage} the handler can
     * handle.
     */
    public static final String QUERY_NAME = "QueryHandler.queryName";
    /**
     * Attribute key referencing the exception result type the handler can handle.
     */
    public static final String EXCEPTION_RESULT_TYPE = "ExceptionHandler.resultType";

    /**
     * Attribute key referencing whether the handler forces the creation of a new saga instance.
     */
    public static final String FORCE_NEW_SAGA = "StartSaga.forceNew";
    /**
     * Attribute key referencing the property in the handled {@link org.axonframework.eventhandling.EventMessage} to
     * associate a saga instance with.
     */
    public static final String SAGA_ASSOCIATION_PROPERTY = "SagaEventHandler.associationProperty";
    /**
     * Attribute key referencing the saga event handler's association property key name used.
     */
    public static final String SAGA_ASSOCIATION_PROPERTY_KEY_NAME = "SagaEventHandler.keyName";
    /**
     * Attribute key referencing the type of association resolver used by a saga event handler.
     */
    public static final String SAGA_ASSOCIATION_RESOLVER = "SagaEventHandler.associationResolver";

    /**
     * Attribute key referencing the {@link org.axonframework.lifecycle.Phase} to invoke a start handler in.
     */
    public static final String START_PHASE = "StartHandler.phase";
    /**
     * Attribute key referencing the {@link org.axonframework.lifecycle.Phase} to invoke a shutdown handler in.
     */
    public static final String SHUTDOWN_PHASE = "ShutdownHandler.phase";

    /**
     * Attribute key referencing an aggregate creation policy to be used when handling a command.
     */
    public static final String AGGREGATE_CREATION_POLICY = "CreationPolicy.creationPolicy";

    /**
     * Attribute key referencing whether the handler is allowed to be invoked on replays.
     */
    public static final String ALLOW_REPLAY = "AllowReplay.allowReplay";

    private HandlerAttributeDictionary() {
        // Utility class
    }
}

