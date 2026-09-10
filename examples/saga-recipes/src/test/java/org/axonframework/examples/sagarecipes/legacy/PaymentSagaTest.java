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
import org.axonframework.examples.sagarecipes.payment.write.rejectpayment.RejectPayment;
import org.axonframework.examples.sagarecipes.rental.BikeId;
import org.axonframework.examples.sagarecipes.rental.RentalId;
import org.axonframework.examples.sagarecipes.rental.event.BikeRequested;
import org.axonframework.examples.sagarecipes.rental.event.RequestRejected;
import org.axonframework.examples.sagarecipes.rental.write.approverequest.ApproveRequest;
import org.axonframework.examples.sagarecipes.rental.write.rejectrequest.RejectRequest;
import org.axonframework.examples.sagarecipes.saga.shared.RentalPaymentReference;
import org.axonframework.examples.sagarecipes.saga.shared.RentalPricing;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Almost identical to the bike rental sample application's own {@code PaymentSagaTest}: same method names, same
 * scenarios, same fixture style, run here against {@link PaymentSaga} through the ported {@link SagaTestFixture}.
 * <p>
 * What differs is only what the domain forced: {@code BikeId}/{@code RentalId}/{@code PaymentId}/
 * {@code PaymentReference} replace the bike rental application's raw strings, and the payment reference is derived
 * with {@link RentalPaymentReference} rather than supplied as a separate literal, since this module's
 * {@code BikeRequested} carries no {@code rentalReference} field of its own. The last scenario, driven by a deadline
 * in the original, is disabled rather than deleted: see its Javadoc.
 *
 * @author Mateusz Nowak
 */
class PaymentSagaTest {

    private static final String RENTER = "renter";

    private SagaTestFixture<PaymentSaga> fixture;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(PaymentSaga.class);
    }

    @AfterEach
    void tearDown() {
        fixture.stop();
    }

    @Test
    void shouldStartSagaOnBikeRequested() {
        var bikeId = BikeId.of("bikeId");
        var rentalId = RentalId.of("rentalRef");
        var reference = RentalPaymentReference.forRental(rentalId);

        fixture.givenNoPriorActivity()
               .whenPublishingA(new BikeRequested(bikeId, RENTER, rentalId))
               .expectDispatchedCommands(new PreparePayment(reference, RentalPricing.PRICE))
               .expectActiveSagas(1);
    }

    @Test
    void shouldAcceptRequestOnPaymentConfirmed() {
        var bikeId = BikeId.of("bikeId");
        var rentalId = RentalId.of("rentalRef");
        var reference = RentalPaymentReference.forRental(rentalId);
        var paymentId = PaymentId.of("paymentId");

        fixture.givenAPublished(new BikeRequested(bikeId, RENTER, rentalId))
               .whenPublishingA(new PaymentConfirmed(paymentId, reference))
               .expectDispatchedCommands(new ApproveRequest(bikeId, RENTER))
               .expectActiveSagas(0);
    }

    @Test
    void shouldRejectRequestOnPaymentRejected() {
        var bikeId = BikeId.of("bikeId");
        var rentalId = RentalId.of("rentalRef");
        var reference = RentalPaymentReference.forRental(rentalId);
        var paymentId = PaymentId.of("paymentId");

        fixture.givenAPublished(new BikeRequested(bikeId, RENTER, rentalId))
               .whenPublishingA(new PaymentRejected(paymentId, reference))
               .expectDispatchedCommands(new RejectRequest(bikeId, RENTER));
    }

    @Test
    void shouldEndSagaWhenRequestIsRejected() {
        var bikeId = BikeId.of("bikeId");
        var rentalId = RentalId.of("rentalRef");

        fixture.givenAPublished(new BikeRequested(bikeId, RENTER, rentalId))
               .whenPublishingA(new RequestRejected(bikeId, RENTER, rentalId))
               .expectActiveSagas(0);
    }

    /**
     * The bike rental sample application drives this from {@code whenTimeElapses(Duration.ofSeconds(30))}. Disabled
     * rather than deleted, to keep this file recognizable next to the original: {@code SagaTestFixture} has the exact
     * same gap {@link PaymentSaga} does, and for the same reason -- see
     * {@link org.axonframework.test.saga.SagaTestFixture#whenTimeElapses}.
     */
    @Disabled("axon-legacy #5006: SagaTestFixture.whenTimeElapses(...) throws UnsupportedOperationException "
            + "until deadlines are ported")
    @Test
    void shouldRejectPaymentWhenNotConfirmedIn30Seconds() {
        var rentalId = RentalId.of("rentalRef");
        var reference = RentalPaymentReference.forRental(rentalId);
        var paymentId = PaymentId.of("paymentId");

        fixture.givenAPublished(new BikeRequested(BikeId.of("bikeId"), RENTER, rentalId))
               .andThenAPublished(new PaymentPrepared(paymentId, RentalPricing.PRICE, reference))
               .whenTimeElapses(Duration.ofSeconds(30))
               .expectDispatchedCommands(new RejectPayment(paymentId));
    }
}
