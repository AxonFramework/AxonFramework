package demo;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;

@Saga
@Slf4j
public class DemoSaga {

    @StartSaga
    @SagaEventHandler(associationProperty = "sagaId")
    public void on(DemoEvent evt) {
        log.error("saga: " + evt);
    }
}
