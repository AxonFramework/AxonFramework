package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
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
    private static final String COMMAND_BUS_CONNECTOR_PATH = "/spring-command-bus-connector";
    private static final String COMMAND_PATH = "/command";

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
                    endpointUri.getHost(), endpointUri.getPort(), COMMAND_PATH);

            SpringHttpDispatchMessage dispatchMessage =
                    new SpringHttpDispatchMessage(commandMessage, serializer, expectReply);
            return restTemplate.exchange(destinationUri, HttpMethod.POST, new HttpEntity<>(dispatchMessage),
                    new ParameterizedTypeReference<SpringHttpReplyMessage<R>>(){});
        } else {
            String errorMessage = String.format("No Connection Endpoint found in Member [%s] for protocol [%s] " +
                    "to send the command message [%s] to", destination, URI.class, commandMessage);
            LOGGER.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private URI buildURIForPath(String scheme, String userInfo, String host, int port, String path) {
        path = COMMAND_BUS_CONNECTOR_PATH + path;
        try {
            return new URI(scheme, userInfo, host, port, path, null, null);
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to build URI for [{}{}{}], with user info [{}] and path [{}]",
                    scheme, host, port, userInfo, path, e);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return localCommandBus.subscribe(commandName, handler);
    }

    @PostMapping("/command")
    public CompletableFuture<SpringHttpReplyMessage> receiveCommand(
            @RequestBody SpringHttpDispatchMessage dispatchMessage) throws ExecutionException, InterruptedException {
        CommandMessage commandMessage = dispatchMessage.getCommandMessage(serializer);
        CompletableFuture<SpringHttpReplyMessage> result = new CompletableFuture<>();

        if (dispatchMessage.isExpectReply()) {
            try {
                result = new SpringHttpReplyFutureCallback<>();
                localCommandBus.dispatch(commandMessage, (SpringHttpReplyFutureCallback) result);
            } catch (Exception e) {
                LOGGER.error("Could not dispatch command", e);
                result.complete(new SpringHttpReplyMessage(commandMessage.getIdentifier(), null, e, serializer));
            }
        } else {
            try {
                localCommandBus.dispatch(commandMessage);
            } catch (Exception e) {
                LOGGER.error("Could not dispatch command", e);
                result.complete(new SpringHttpReplyMessage(commandMessage.getIdentifier(), null, e, serializer));
            }
        }

        return result;
    }

    private class SpringHttpReplyFutureCallback<C> extends FutureCallback<C, SpringHttpReplyMessage> {

        @Override
        public void onSuccess(CommandMessage<? extends C> commandMessage, SpringHttpReplyMessage result) {
            super.complete(new SpringHttpReplyMessage(commandMessage.getIdentifier(), result, null, serializer));
        }

        @Override
        public void onFailure(CommandMessage commandMessage, Throwable cause) {
            super.complete(new SpringHttpReplyMessage(commandMessage.getIdentifier(), null, cause, serializer));
        }

    }

}
