package demo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class DemoEvent {
    String aggId;

    String sagaId;

    @JsonCreator
    public DemoEvent(
            @JsonProperty("aggId") String aggId,
            @JsonProperty("sagaId") String sagaId) {
        this.aggId = aggId;
        this.sagaId = sagaId;
    }
}
