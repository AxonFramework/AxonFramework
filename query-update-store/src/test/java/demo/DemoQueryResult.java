package demo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class DemoQueryResult implements Serializable {

    String aggId;

    @JsonCreator
    public DemoQueryResult(@JsonProperty("aggId") String aggId) {
        this.aggId = aggId;
    }
}
