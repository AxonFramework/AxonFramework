package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        LOGGER.info(String.format("send [%s] to [%s]", destination, commandMessage));

        destination.getConnectionEndpoint(URI.class).ifPresent(connectionEndpoint -> {
            URI destinationUri = buildURIForPath(connectionEndpoint.getScheme(), connectionEndpoint.getUserInfo(),
                    connectionEndpoint.getHost(), connectionEndpoint.getPort(), COMMAND_PATH);

            SpringHttpDispatchMessage dispatchMessage =
                    new SpringHttpDispatchMessage(commandMessage, serializer, DO_NOT_EXPECT_REPLY);
            restTemplate.postForEntity(destinationUri, dispatchMessage, SpringHttpReplyMessage.class);
        });
    }

    @Override
    public <C, R> void send(Member destination, CommandMessage<C> commandMessage,
                            CommandCallback<? super C, R> callback) throws Exception {
        LOGGER.info(String.format("send [%s] to [%s] with callback [%s]", destination, commandMessage, callback));

        destination.getConnectionEndpoint(URI.class).ifPresent(connectionEndpoint -> {
            URI destinationUri = buildURIForPath(connectionEndpoint.getScheme(), connectionEndpoint.getUserInfo(),
                    connectionEndpoint.getHost(), connectionEndpoint.getPort(), COMMAND_PATH);

            SpringHttpDispatchMessage dispatchMessage =
                    new SpringHttpDispatchMessage(commandMessage, serializer, EXPECT_REPLY);

            SpringHttpReplyMessage callbackMessage = restTemplate
                    .postForEntity(destinationUri, dispatchMessage, SpringHttpReplyMessage.class)
                    .getBody();
            if (callbackMessage.isSuccess()) {
                callback.onSuccess(commandMessage, (R) callbackMessage.getReturnValue(serializer));
            } else {
                callback.onFailure(commandMessage, callbackMessage.getError(serializer));
            }
        });
    }

    private URI buildURIForPath(String scheme, String userInfo, String host, int port, String path) {
        path = COMMAND_BUS_CONNECTOR_PATH + path;
        try {
            return new URI(scheme, userInfo, host, port, path, null, null);
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to adapt URI for {}{}{}, user info {} and path {}", scheme, host, port, userInfo, path, e);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        LOGGER.info(String.format("subscribe [%s] for handler [%s]", commandName, handler));
        return localCommandBus.subscribe(commandName, handler);
    }

    @PostMapping("/command")
    public CompletableFuture<SpringHttpReplyMessage> receiveCommand(
            @RequestBody SpringHttpDispatchMessage dispatchMessage) throws ExecutionException, InterruptedException {
        LOGGER.info(String.format("received dispatchMessage [%s]", dispatchMessage));
        CompletableFuture<SpringHttpReplyMessage> result = new CompletableFuture<>();

        CommandMessage commandMessage = dispatchMessage.getCommandMessage(serializer);
        if (dispatchMessage.isExpectReply()) {
            try {
                result = new SpringHttpFutureCallback<>();
                localCommandBus.dispatch(commandMessage, (SpringHttpFutureCallback) result);
            } catch (Exception e) {
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

    private class SpringHttpFutureCallback<C, R> extends CompletableFuture<SpringHttpReplyMessage> implements CommandCallback<C, R> {

        @Override
        public void onSuccess(CommandMessage<? extends C> commandMessage, R executionResult) {
            super.complete(new SpringHttpReplyMessage(commandMessage.getIdentifier(), executionResult, null, serializer));
        }

        @Override
        public void onFailure(CommandMessage commandMessage, Throwable cause) {
            super.complete(new SpringHttpReplyMessage(commandMessage.getIdentifier(), null, cause, serializer));
        }

        /**
         * Waits if necessary for the command handling to complete, and then returns its result.
         * <p/>
         * Unlike {@link #get(long, java.util.concurrent.TimeUnit)}, this method will throw the original exception. Only
         * checked exceptions are wrapped in a {@link CommandExecutionException}.
         * <p/>
         * If the thread is interrupted while waiting, the interrupt flag is set back on the thread, and {@code null}
         * is returned. To distinguish between an interrupt and a {@code null} result, use the {@link #isDone()}
         * method.
         *
         * @return the result of the command handler execution.
         * @see #get()
         */
        public SpringHttpReplyMessage getResult() {
            try {
                return get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                throw asRuntime(e);
            }
        }

        /**
         * Waits if necessary for at most the given time for the command handling to complete, and then retrieves its
         * result, if available.
         * <p/>
         * Unlike {@link #get(long, java.util.concurrent.TimeUnit)}, this method will throw the original exception. Only
         * checked exceptions are wrapped in a {@link CommandExecutionException}.
         * <p/>
         * If the timeout expired or the thread is interrupted before completion, {@code null} is returned. In case of
         * an interrupt, the interrupt flag will have been set back on the thread. To distinguish between an interrupt and
         * a {@code null} result, use the {@link #isDone()}
         *
         * @param timeout the maximum time to wait
         * @param unit    the time unit of the timeout argument
         * @return the result of the command handler execution.
         */
        public SpringHttpReplyMessage getResult(long timeout, TimeUnit unit) {
            try {
                return get(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (TimeoutException e) {
                return null;
            } catch (ExecutionException e) {
                throw asRuntime(e);
            }
        }

        private RuntimeException asRuntime(Exception e) {
            Throwable failure = e.getCause();
            if (failure instanceof Error) {
                throw (Error) failure;
            } else if (failure instanceof RuntimeException) {
                return (RuntimeException) failure;
            } else {
                return new CommandExecutionException("An exception occurred while executing a command", failure);
            }
        }

        /**
         * Wait for completion of the command, or for the timeout to expire.
         *
         * @param timeout The amount of time to wait for command processing to complete
         * @param unit    The unit in which the timeout is expressed
         * @return {@code true} if command processing completed before the timeout expired, otherwise
         * {@code false}.
         */
        public boolean awaitCompletion(long timeout, TimeUnit unit) {
            try {
                get(timeout, unit);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException e) {
                return true;
            } catch (TimeoutException e) {
                return false;
            }
        }
    }

}
