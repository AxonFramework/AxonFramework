package demo;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DemoEventEmitter {

    @Autowired
    private QueryUpdateEmitter queryUpdateEmitter;

    @EventHandler
    public void on(DemoEvent evt) {
        log.error("emitter: " + evt);

        queryUpdateEmitter.emit(DemoQuery.class, //
                q -> evt.getAggId().equals(q.getAggId()), //
                new DemoQueryResult(evt.getAggId()));
    }

    @QueryHandler
    public DemoQueryResult handle(DemoQuery q) {
        return new DemoQueryResult(q.getAggId());
    }
}
