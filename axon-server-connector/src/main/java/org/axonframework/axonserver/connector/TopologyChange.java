/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.control.UpdateType;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Object representing a topology change at Axon Server.
 * <p>
 * Topology changes can be acted upon by registering a {@link TopologyChangeListener}.
 *
 * @author Steven van Beelen
 * @see TopologyChangeListener
 * @since 4.12.0
 */
public class TopologyChange {

    private final Type type;
    private final String context;
    private final String clientId;
    private final String clientStreamId;
    private final String componentName;
    private final HandlerSubscription handlerSubscription;

    /**
     * Constructs a {@code TopologyChange} based on the give {@code type}, {@code context}, {@code clientId},
     * {@code clientStreamId}, {@code componentName}, and {@code handler}.
     *
     * @param type                The type of the {@code TopologyChange}.
     * @param context             The {@link AxonServerConfiguration#getContext() context} for which a
     *                            {@code TopologyChange} occurred.
     * @param clientId            The {@link AxonServerConfiguration#getClientId() client identifier} for which a
     *                            {@code TopologyChange} occurred.
     * @param clientStreamId      The client stream identifier for which a {@code TopologyChange} occurred.
     * @param componentName       The {@link AxonServerConfiguration#getComponentName() component name} for which a
     *                            {@code TopologyChange} occurred.
     * @param handlerSubscription The {@link HandlerSubscription} for which a {@code TopologyChange} occurred. Should be
     *                            {@code null} for the {@link Type#RESET Type RESET}.
     */
    public TopologyChange(Type type,
                          String context,
                          String clientId,
                          String clientStreamId,
                          String componentName,
                          HandlerSubscription handlerSubscription) {
        this.type = type;
        this.context = context;
        this.clientId = clientId;
        this.clientStreamId = clientStreamId;
        this.componentName = componentName;
        this.handlerSubscription = handlerSubscription;
    }

    /**
     * Constructs a {@code TopologyChange} based on the given gRPC {@code change}.
     *
     * @param change The gRPC {@code TopologyChange} to construct an Axon Framework {@code TopologyChange} from.
     */
    public TopologyChange(io.axoniq.axonserver.grpc.control.TopologyChange change) {
        this.type = mapToType(change.getUpdateType());
        this.context = change.getContext();
        this.clientId = change.getClientId();
        this.clientStreamId = change.getClientStreamId();
        this.componentName = change.getComponentName();
        if (change.hasCommand()) {
            this.handlerSubscription = new HandlerSubscription(change.getCommand().getName(),
                                                               change.getCommand().getLoadFactor());
        } else if (change.hasQuery()) {
            this.handlerSubscription = new HandlerSubscription(change.getQuery().getName(), null);
        } else {
            this.handlerSubscription = null;
        }
    }

    private Type mapToType(UpdateType updateType) {
        return switch (updateType) {
            case ADD_COMMAND_HANDLER -> Type.COMMAND_HANDLER_ADDED;
            case REMOVE_COMMAND_HANDLER -> Type.COMMAND_HANDLER_REMOVED;
            case ADD_QUERY_HANDLER -> Type.QUERY_HANDLER_ADDED;
            case REMOVE_QUERY_HANDLER -> Type.QUERY_HANDLER_REMOVED;
            case RESET_ALL -> Type.RESET;
            case UNRECOGNIZED -> null;
        };
    }

    /**
     * Returns the {@code Type} of the {@code TopologyChange}.
     *
     * @return The {@code Type} of the {@code TopologyChange}.
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the {@link AxonServerConfiguration#getContext() context} for which a {@code TopologyChange} occurred.
     *
     * @return The {@link AxonServerConfiguration#getContext() context} for which a {@code TopologyChange} occurred.
     */
    public String context() {
        return context;
    }

    /**
     * Returns the {@link AxonServerConfiguration#getClientId() client identifier} for which a {@code TopologyChange}
     * occurred.
     *
     * @return The {@link AxonServerConfiguration#getClientId() client identifier} for which a {@code TopologyChange}
     * occurred.
     */
    public String clientId() {
        return clientId;
    }

    /**
     * Returns the client stream identifier for which a {@code TopologyChange} occurred.
     *
     * @return The client stream identifier for which a {@code TopologyChange} occurred.
     */
    public String clientStreamId() {
        return clientStreamId;
    }

    /**
     * Returns the {@link AxonServerConfiguration#getComponentName() component name} for which a {@code TopologyChange}
     * occurred.
     *
     * @return The {@link AxonServerConfiguration#getComponentName() component name} for which a {@code TopologyChange}
     * occurred.
     */
    public String componentName() {
        return componentName;
    }

    /**
     * Returns the {@code HandlerSubscription} for which a {@code TopologyChange} occurred.
     * <p>
     * Is {@code null} when {@link #type()} returns {@link Type#RESET}.
     *
     * @return The {@code HandlerSubscription} for which a {@code TopologyChange} occurred.
     */
    public HandlerSubscription handler() {
        return handlerSubscription;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TopologyChange that = (TopologyChange) o;
        return type == that.type
                && Objects.equals(context, that.context)
                && Objects.equals(clientId, that.clientId)
                && Objects.equals(clientStreamId, that.clientStreamId)
                && Objects.equals(componentName, that.componentName)
                && Objects.equals(handlerSubscription, that.handlerSubscription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, context, clientId, clientStreamId, componentName, handlerSubscription);
    }

    @Override
    public String toString() {
        return "TopologyChange{" +
                "type=" + type +
                ", context='" + context + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientStreamId='" + clientStreamId + '\'' +
                ", componentName='" + componentName + '\'' +
                ", handlerSubscription=" + handlerSubscription +
                '}';
    }

    /**
     * The enumeration referencing the possible {@code TopologyChange} types.
     */
    public enum Type {
        /**
         * A change type signalling a command handler was added.
         */
        COMMAND_HANDLER_ADDED,
        /**
         * A change type signalling a command handler was removed.
         */
        COMMAND_HANDLER_REMOVED,
        /**
         * A change type signalling a query handler was added.
         */
        QUERY_HANDLER_ADDED,
        /**
         * A change type signalling a query handler was removed.
         */
        QUERY_HANDLER_REMOVED,
        /**
         * A change type signalling the connection broke down, willingly or not, resulting in a complete reset.
         */
        RESET,
    }

    /**
     * The handler subscription of a {@link TopologyChange}.
     * <p>
     * Should be {@code null} whenever the {@link #type()} is {@link Type#RESET}.
     */
    public static class HandlerSubscription {

        private final String name;
        private final Integer loadFactor;

        /**
         * Constructs a {@code HandlerSubscription} for the given {@code name} and {@code loadFactor}.
         *
         * @param name       The name of the {@code HandlerSubscription}.
         * @param loadFactor The load factor of the {@code HandlerSubscription}. Should be {@code null} when this
         *                   {@code HandlerSubscription} is used when {@link #type()} contains
         *                   {@link Type#QUERY_HANDLER_ADDED} or {@link Type#QUERY_HANDLER_REMOVED}.
         */
        public HandlerSubscription(String name, Integer loadFactor) {
            this.name = name;
            this.loadFactor = loadFactor;
        }

        /**
         * Returns the name of the {@code HandlerSubscription}.
         *
         * @return The name of the {@code HandlerSubscription}.
         */
        public String name() {
            return name;
        }

        /**
         * Returns the load factor of the {@code HandlerSubscription}.
         * <p>
         * Should be {@code null} when this {@code HandlerSubscription} is used when {@link #type()} contains
         * {@link Type#QUERY_HANDLER_ADDED} or {@link Type#QUERY_HANDLER_REMOVED}.
         *
         * @return The load factor of the {@code HandlerSubscription}.
         */
        public OptionalInt loadFactor() {
            return loadFactor != null ? OptionalInt.of(loadFactor) : OptionalInt.empty();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HandlerSubscription that = (HandlerSubscription) o;
            return Objects.equals(name, that.name)
                    && Objects.equals(loadFactor, that.loadFactor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, loadFactor);
        }

        @Override
        public String toString() {
            return "HandlerSubscription{" +
                    "name='" + name + '\'' +
                    ", loadFactor=" + loadFactor +
                    '}';
        }
    }
}
