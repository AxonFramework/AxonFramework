/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import javax.websocket.server.ServerEndpointConfig;

public class WebsocketCommandBusConnectorServerConfigurator extends ServerEndpointConfig.Configurator {
    private static CommandBus localSegment;
    private static Serializer serializer = new XStreamSerializer();

    public static CommandBus getLocalSegment() {
        return localSegment;
    }

    public static void setLocalSegment(CommandBus localSegment) {
        WebsocketCommandBusConnectorServerConfigurator.localSegment = localSegment;
    }

    public static void setSerializer(Serializer serializer) {
        WebsocketCommandBusConnectorServerConfigurator.serializer = serializer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (endpointClass.isAssignableFrom(WebsocketCommandBusConnectorServer.class)) {
            return (T) new WebsocketCommandBusConnectorServer(localSegment, serializer);
        } else {
            return super.getEndpointInstance(endpointClass);
        }
    }
}
