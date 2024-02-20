package org.axonframework.spring.authorization;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


/**
 * Message interceptor that verifies authorization based on {@code @PreAuthorize} annotations on commands
 *
 * @author Roald Bankras
 */
public class CommandAuthorizationInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {

    private static final Logger log = LoggerFactory.getLogger(CommandAuthorizationInterceptor.class);

    @Override
    public Object handle(UnitOfWork<? extends CommandMessage<?>> unitOfWork,
                         @javax.annotation.Nonnull InterceptorChain interceptorChain) throws Exception {
        CommandMessage<?> command = unitOfWork.getMessage();
        PreAuthorize annotation = command.getPayloadType().getAnnotation(PreAuthorize.class);
        Set<GrantedAuthority> userId = Optional.ofNullable(command.getMetaData().get("authorities"))
                                               .map(uId -> {
                                                   log.debug("Found authorities: {}", uId);
                                                   return new HashSet<>((List<GrantedAuthority>) uId);
                                               })
                                               .orElseThrow(() -> new UnauthorizedCommandException(
                                                       "No authorities found"));

        log.debug("Authorizing for {} and {}", command.getCommandName(), annotation.value());
        if (userId.contains(new SimpleGrantedAuthority(annotation.value()))) {
            return interceptorChain.proceed();
        }
        throw new UnauthorizedCommandException("Unauthorized command");
    }
}

