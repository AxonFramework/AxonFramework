package demo;

import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Value
public class DemoCommand {
    @TargetAggregateIdentifier
    String aggId;

    String sagaId;
}
