package demo;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@org.springframework.web.bind.annotation.RestController
public class DemoController {

    @Autowired
    CommandGateway commandGateway;

    @Autowired
    QueryGateway queryGateway;

    @RequestMapping
    @ResponseBody
    public String trigger(@CookieValue(name = "sagaId", required = false) String sagaId) {
        String aggId = UUID.randomUUID().toString();
        log.error("aggId: " + aggId + "   " + "sagaId: " + sagaId);

        SubscriptionQueryResult<DemoQueryResult, DemoQueryResult> queryResult = queryGateway.subscriptionQuery( //
                new DemoQuery(aggId), //
                ResponseTypes.instanceOf(DemoQueryResult.class), //
                ResponseTypes.instanceOf(DemoQueryResult.class));

        try {
            commandGateway.send(new DemoCommand(aggId, sagaId));

            return queryResult.updates() //
                    .blockFirst(Duration.ofSeconds(3)) //
                    .getAggId();
        } finally {
            queryResult.cancel();
        }
    }
}
