package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/spring-command-bus-connector")
public class SpringHttpCommandBusConnector implements CommandBusConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringHttpCommandBusConnector.class);

    private static final boolean EXPECT_REPLY = true;
    private static final boolean DO_NOT_EXPECT_REPLY = false;
    private static final String COMMAND_BUS_CONNECTOR_PATH = "/spring-command-bus-connector/command";

    private final CommandBus localCommandBus;
    private final RestTemplate restTemplate;
    private final Serializer serializer;

    public SpringHttpCommandBusConnector(CommandBus localCommandBus, RestTemplate restTemplate, Serializer serializer) {
        this.localCommandBus = localCommandBus;
        this.restTemplate = restTemplate;
        this.serializer = serializer;
    }

    @Override
    public <C> void send(Member destination, CommandMessage<? extends C> commandMessage) throws Exception {
        doSend(destination, commandMessage, DO_NOT_EXPECT_REPLY);
    }

    @Override
    public <C, R> void send(Member destination, CommandMessage<C> commandMessage,
                            CommandCallback<? super C, R> callback) throws Exception {
        SpringHttpReplyMessage<R> replyMessage = this.<C, R>doSend(destination, commandMessage, EXPECT_REPLY).getBody();
        if (replyMessage.isSuccess()) {
            callback.onSuccess(commandMessage, replyMessage.getReturnValue(serializer));
        } else {
            callback.onFailure(commandMessage, replyMessage.getError(serializer));
        }
    }

    private <C, R> ResponseEntity<SpringHttpReplyMessage<R>> doSend(Member destination,
                                                                    CommandMessage<? extends C> commandMessage,
                                                                    boolean expectReply) {
        Optional<URI> optionalEndpoint = destination.getConnectionEndpoint(URI.class);
        if (optionalEndpoint.isPresent()) {
            URI endpointUri = optionalEndpoint.get();
            URI destinationUri = buildURIForPath(endpointUri.getScheme(), endpointUri.getUserInfo(),
                    endpointUri.getHost(), endpointUri.getPort());

            SpringHttpDispatchMessage<C> dispatchMessage =
                    new SpringHttpDispatchMessage<>(commandMessage, serializer, expectReply);
            return restTemplate.exchange(destinationUri, HttpMethod.POST, new HttpEntity<>(dispatchMessage),
                    new ParameterizedTypeReference<SpringHttpReplyMessage<R>>(){});
        } else {
            String errorMessage = String.format("No Connection Endpoint found in Member [%s] for protocol [%s] " +
                    "to send the command message [%s] to", destination, URI.class, commandMessage);
            LOGGER.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private URI buildURIForPath(String scheme, String userInfo, String host, int port) {
        try {
            return new URI(scheme, userInfo, host, port, COMMAND_BUS_CONNECTOR_PATH, null, null);
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to build URI for [{}{}{}], with user info [{}] and path [{}]",
                    scheme, host, port, userInfo, COMMAND_BUS_CONNECTOR_PATH, e);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return localCommandBus.subscribe(commandName, handler);
    }

    @PostMapping("/command")
    public <C, R> CompletableFuture<?> receiveCommand(
            @RequestBody SpringHttpDispatchMessage<C> dispatchMessage) throws ExecutionException, InterruptedException {
        CommandMessage<C> commandMessage = dispatchMessage.getCommandMessage(serializer);
        if (dispatchMessage.isExpectReply()) {
            try {
                SpringHttpReplyFutureCallback<C, R> replyFutureCallback = new SpringHttpReplyFutureCallback<>();
                localCommandBus.dispatch(commandMessage, replyFutureCallback);
                return replyFutureCallback;
            } catch (Exception e) {
                LOGGER.error("Could not dispatch command", e);
                return CompletableFuture.completedFuture(createReply(commandMessage, false, e));
            }
        } else {
            try {
                localCommandBus.dispatch(commandMessage);
                return CompletableFuture.completedFuture("");
            } catch (Exception e) {
                LOGGER.error("Could not dispatch command", e);
                return CompletableFuture.completedFuture(createReply(commandMessage, false, e));
            }
        }
    }

    private SpringHttpReplyMessage createReply(CommandMessage<?> commandMessage, boolean success, Object result) {
        try {
            return new SpringHttpReplyMessage<>(commandMessage.getIdentifier(), success, result, serializer);
        } catch (Exception e) {
            LOGGER.warn("Could not serialize command reply [{}]. Sending back NULL.", result, e);
            return new SpringHttpReplyMessage(commandMessage.getIdentifier(), success, null, serializer);
        }
    }

    public class SpringHttpReplyFutureCallback<C, R>  extends CompletableFuture<SpringHttpReplyMessage>
            implements CommandCallback<C, R> {

        @Override
        public void onSuccess(CommandMessage<? extends C> commandMessage, R result) {
            super.complete(createReply(commandMessage, true, result));
        }

        @Override
        public void onFailure(CommandMessage commandMessage, Throwable cause) {
            super.complete(createReply(commandMessage, false, cause));
        }

    }

}
