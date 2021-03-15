package org.axonframework.messaging;

import java.util.Map;

/**
 * Container for message handler attributes. Typically used by {@link org.axonframework.messaging.annotation.MessageHandlingMember}
 * implementations. Stores handler attributes in a {@link Map} of {@link String} to {@link Object}. Some default keys
 * used by {@link HandlerAttributes} implementations, like {@link #MESSAGE_TYPE} can be used to {@link #get(String)}
 * entries.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public interface HandlerAttributes {

    /**
     * Attribute key referencing the {@link org.axonframework.messaging.Message} type being handled by the handler.
     */
    String MESSAGE_TYPE = "MessageHandler.messageType";
    /**
     * Attribute key referencing the payload type contained in the {@link org.axonframework.messaging.Message}.
     */
    String PAYLOAD_TYPE = "MessageHandler.payloadType";
    /**
     * Attribute key referencing the name of the {@link org.axonframework.commandhandling.CommandMessage} the handler
     * can handle.
     */
    String COMMAND_NAME = "CommandHandler.commandName";
    /**
     * Attribute key referencing the routing key used to route a {@link org.axonframework.commandhandling.CommandMessage}
     * to the handler.
     */
    String COMMAND_ROUTING_KEY = "CommandHandler.routingKey";
    /**
     * Attribute key referencing the name of the {@link org.axonframework.queryhandling.QueryMessage} the handler can
     * handle.
     */
    String QUERY_NAME = "QueryHandler.queryName";

    /**
     * Attribute key referencing the result type the handler can handle.
     */
    String RESULT_TYPE = "ResultHandler.resultType";
    /**
     * Attribute key referencing the exception result type the handler can handle.
     */
    String EXCEPTION_RESULT_TYPE = "ExceptionHandler.resultType";

    /**
     * Attribute key referencing whether the handler forces the creation of a new saga instance.
     */
    String FORCE_NEW_SAGA = "StartSaga.forceNew";
    /**
     * Attribute key referencing the property in the handled {@link org.axonframework.eventhandling.EventMessage} to
     * associate a saga instance with.
     */
    String SAGA_ASSOCIATION_PROPERTY = "SagaEventHandler.associationProperty";
    /**
     * Attribute key referencing the saga event handler's association property key name used.
     */
    String SAGA_ASSOCIATION_PROPERTY_KEY_NAME = "SagaEventHandler.keyName";
    /**
     * Attribute key referencing the type of association resolver used by a saga event handler.
     */
    String SAGA_ASSOCIATION_RESOLVER = "SagaEventHandler.associationResolver";

    /**
     * Attribute key referencing the {@link org.axonframework.lifecycle.Phase} to invoke a start handler in.
     */
    String START_PHASE = "StartHandler.phase";
    /**
     * Attribute key referencing the {@link org.axonframework.lifecycle.Phase} to invoke a shutdown handler in.
     */
    String SHUTDOWN_PHASE = "ShutdownHandler.phase";

    /**
     * Attribute key referencing an aggregate creation policy to be used when handling a command.
     */
    String AGGREGATE_CREATION_POLICY = "CreationPolicy.creationPolicy";

    /**
     * Attribute key referencing whether the handler is allowed to be invoked on replays.
     */
    String ALLOW_REPLAY = "AllowReplay.allowReplay";

    /**
     * Retrieve the attribute for the given {@code attributeKey}. Might be {@code null} if there is no attribute present
     * for the given key.
     *
     * @param attributeKey the attribute key to retrieve an attribute for
     * @param <R>          the type of attribute to retrieve
     * @return the attribute for the given {@code attributeKey}
     */
    <R> R get(String attributeKey);

    /**
     * Retrieve all attributes stored in this {@link HandlerAttributes} object.
     *
     * @return all attributes stored in this {@link HandlerAttributes} object
     */
    Map<String, Object> getAll();

    /**
     * Validates whether the given {@code attributeKey} is present in this object.
     *
     * @param attributeKey the attribute key to validate if it is present in this object
     * @return {@code true} if there is an attribute for the given {@code attributeKey} present, {@code false} otherwise
     */
    boolean contains(String attributeKey);

    /**
     * Validate whether zero attributes are present in this object.
     *
     * @return {@code true} if there are no attributes present, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Returns a {@link HandlerAttributes}, merging the attributes in {@code this} instance with the given {@code
     * attributes}. If {@code this} and {@code other} have identical entries, the values from {@code other} will take
     * precedence.
     *
     * @param other the {@link HandlerAttributes} to group with {@code this} instance's attributes
     * @return a {@link HandlerAttributes} combining {@code this} instance's attributes and the given {@code other}
     * attributes
     */
    HandlerAttributes mergedWith(HandlerAttributes other);
}
