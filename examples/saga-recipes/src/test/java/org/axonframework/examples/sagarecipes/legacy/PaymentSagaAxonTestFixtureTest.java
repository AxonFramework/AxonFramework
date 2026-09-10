/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.examples.sagarecipes.legacy;

import org.axonframework.examples.sagarecipes.payment.PaymentId;
import org.axonframework.examples.sagarecipes.payment.event.PaymentConfirmed;
import org.axonframework.examples.sagarecipes.payment.event.PaymentPrepared;
import org.axonframework.examples.sagarecipes.payment.event.PaymentRejected;
import org.axonframework.examples.sagarecipes.payment.write.preparepayment.PreparePayment;
import org.axonframework.examples.sagarecipes.rental.BikeId;
import org.axonframework.examples.sagarecipes.rental.RentalId;
import org.axonframework.examples.sagarecipes.rental.event.BikeRegistered;
import org.axonframework.examples.sagarecipes.rental.event.BikeRequested;
import org.axonframework.examples.sagarecipes.rental.write.approverequest.ApproveRequest;
import org.axonframework.examples.sagarecipes.rental.write.rejectrequest.RejectRequest;
import org.axonframework.examples.sagarecipes.saga.SagaRecipeAssertions;
import org.axonframework.examples.sagarecipes.saga.shared.RentalPaymentReference;
import org.axonframework.examples.sagarecipes.saga.shared.RentalPricing;
import org.axonframework.extension.springboot.test.AxonSpringBootTest;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

/**
 * Runs {@link PaymentSaga} through {@link AxonTestFixture}, the same Spring Boot testing style the other four
 * recipes in this module use, reusing their shared {@link SagaRecipeAssertions}.
 * <p>
 * Deliberately not a subclass of {@code SagaRecipeContractTest}: only the three scenarios below hold for this Saga
 * unmodified. The other four in the shared contract test behaviour that exists in the other recipes only because
 * they have no deadline manager to lean on (actively cancelling a payment, handling a redelivered trigger, giving up
 * on request); see {@link PaymentSaga}'s class Javadoc for why this one does not attempt them. Extending the contract
 * class here would mean either failing four inherited tests or silently overriding them away, and both hide the
 * comparison this class exists to make plainly instead.
 *
 * @author Mateusz Nowak
 */
@AxonSpringBootTest(properties = "saga.recipe=legacy")
class PaymentSagaAxonTestFixtureTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private AxonTestFixture fixture;

    private final String renter = "renter-" + UUID.randomUUID();

    @Test
    void givenBikeRequestedThenPaymentIsPrepared() {
        var bikeId = BikeId.random();
        var rentalId = RentalId.random();

        fixture.given()
               .events(new BikeRegistered(bikeId, "city", "Vilnius"),
                       new BikeRequested(bikeId, renter, rentalId))
               .then()
               .await(result -> result.commandsSatisfy(commands -> SagaRecipeAssertions.assertDispatched(
                       commands,
                       new PreparePayment(RentalPaymentReference.forRental(rentalId), RentalPricing.PRICE)
               )), TIMEOUT);
    }

    @Test
    void givenPaymentConfirmedThenRequestApproved() {
        var bikeId = BikeId.random();
        var rentalId = RentalId.random();
        var reference = RentalPaymentReference.forRental(rentalId);
        var paymentId = PaymentId.random();

        fixture.given()
               .events(new BikeRegistered(bikeId, "city", "Vilnius"),
                       new BikeRequested(bikeId, renter, rentalId),
                       new PaymentPrepared(paymentId, RentalPricing.PRICE, reference),
                       new PaymentConfirmed(paymentId, reference))
               .then()
               .await(result -> result.commandsSatisfy(
                       commands -> SagaRecipeAssertions.assertDispatched(commands, new ApproveRequest(bikeId, renter))
               ), TIMEOUT);
    }

    @Test
    void givenPaymentRejectedThenRequestRejected() {
        var bikeId = BikeId.random();
        var rentalId = RentalId.random();
        var reference = RentalPaymentReference.forRental(rentalId);
        var paymentId = PaymentId.random();

        fixture.given()
               .events(new BikeRegistered(bikeId, "city", "Vilnius"),
                       new BikeRequested(bikeId, renter, rentalId),
                       new PaymentPrepared(paymentId, RentalPricing.PRICE, reference),
                       new PaymentRejected(paymentId, reference))
               .then()
               .await(result -> result.commandsSatisfy(
                       commands -> SagaRecipeAssertions.assertDispatched(commands, new RejectRequest(bikeId, renter))
               ), TIMEOUT);
    }
}
