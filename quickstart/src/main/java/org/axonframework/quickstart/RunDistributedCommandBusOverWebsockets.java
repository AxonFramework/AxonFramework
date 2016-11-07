package org.axonframework.quickstart;

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.StaticCommandRouter;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.distributed.websockets.WebsocketCommandBusConnector;
import org.axonframework.commandhandling.distributed.websockets.WebsocketCommandBusConnectorServer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

import javax.websocket.server.ServerEndpointConfig;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

public class RunDistributedCommandBusOverWebsockets {
    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(10000);
        CommandBus commandBus = new SimpleCommandBus();

        Server server = new Server();

        ServerConnector serverConnector = new ServerConnector(server);
        serverConnector.setPort(8080);
        server.addConnector(serverConnector);

        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        server.setHandler(servletContextHandler);


        // Initialize javax.websocket layer
        ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(servletContextHandler);
        ServerEndpointConfig config = ServerEndpointConfig.Builder
                .create(WebsocketCommandBusConnectorServer.class, "/test")
                .configurator(
                        new ServerEndpointConfig.Configurator() {
                            @Override
                            public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                                return endpointClass.cast(new WebsocketCommandBusConnectorServer(commandBus, new XStreamSerializer()));
                            }
                        }
                ).build();

        // Add WebSocket endpoint to javax.websocket layer
        wscontainer.addEndpoint(config);


        commandBus.subscribe("java.lang.String", CommandMessage::getPayload);

        try (WebsocketCommandBusConnector connector = new WebsocketCommandBusConnector(commandBus)) {
            DistributedCommandBus distributedCommandBus = new DistributedCommandBus(
                    new StaticCommandRouter(
                            payload -> (String) payload.getPayload(),
                            new StaticCommandRouter.ServiceMember<>("test",
                                    new URI("ws://localhost:8080/test"), 100, AcceptAll.INSTANCE)),
                    connector);

            server.start();
            server.dump(System.err);

            CommandCallback<String, String> callback = new CommandCallback<String, String>() {
                @Override
                public void onSuccess(CommandMessage<? extends String> commandMessage, String result) {
                    if (!result.equals(commandMessage.getPayload())) {
                        System.err.println("Expected " + commandMessage.getPayload() + ", but got " + result);
                    }
                    if (latch.getCount() % 1000 == 0) {
                        System.out.println(latch.getCount() + " messages to send");
                    }
                    latch.countDown();
                }

                @Override
                public void onFailure(CommandMessage<? extends String> commandMessage, Throwable cause) {
                    cause.printStackTrace();
                }
            };

            for (int i = 0; i < 10000; i++) {
                distributedCommandBus.dispatch(new GenericCommandMessage<>("test" + i), callback);
            }

            latch.await();
        }

        server.stop();
    }
}
