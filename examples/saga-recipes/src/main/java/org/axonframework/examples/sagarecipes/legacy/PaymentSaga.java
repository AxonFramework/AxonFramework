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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.examples.sagarecipes.payment.PaymentReference;
import org.axonframework.examples.sagarecipes.payment.event.PaymentConfirmed;
import org.axonframework.examples.sagarecipes.payment.event.PaymentPrepared;
import org.axonframework.examples.sagarecipes.payment.event.PaymentRejected;
import org.axonframework.examples.sagarecipes.payment.write.preparepayment.PreparePayment;
import org.axonframework.examples.sagarecipes.rental.BikeId;
import org.axonframework.examples.sagarecipes.rental.event.BikeRequested;
import org.axonframework.examples.sagarecipes.rental.event.RequestRejected;
import org.axonframework.examples.sagarecipes.rental.write.approverequest.ApproveRequest;
import org.axonframework.examples.sagarecipes.rental.write.rejectrequest.RejectRequest;
import org.axonframework.examples.sagarecipes.saga.shared.RentalPaymentReference;
import org.axonframework.examples.sagarecipes.saga.shared.RentalPricing;
import org.axonframework.extension.spring.stereotype.Saga;
import org.axonframework.messaging.commandhandling.gateway.CommandDispatcher;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.TimeUnit;

/**
 * The rental payment process, exactly as the bike rental sample application wrote it for Axon Framework 4, ported
 * through {@code axon-legacy} with as few changes as the two APIs let it get away with.
 * <p>
 * Two things could not be carried over unchanged:
 * <ul>
 *     <li><b>{@code SagaLifecycle} is no longer static.</b> Axon Framework 4 resolved it through a
 *     {@code ThreadLocal}; here it is a plain parameter on a {@link SagaEventHandler @SagaEventHandler} method,
 *     resolved for the {@code ProcessingContext} that is active while this instance handles that one event.</li>
 *     <li><b>Deadlines do not exist in {@code axon-legacy} yet.</b> The original scheduled a {@code retryPayment}
 *     deadline when {@code PreparePayment} failed, and a {@code cancelPayment} deadline to give up on an unconfirmed
 *     payment after 30 seconds. Both are commented out below, marked with a TODO. Until they land, this Saga simply
 *     does not retry a failed dispatch and does not time out an unconfirmed payment -- which is also why it does not
 *     run the same contract test the other four recipes in this module do: that contract includes the two scenarios
 *     only the deadline-less recipes had to invent a replacement for, and this Saga, being the unmodified original,
 *     was never asked to.</li>
 * </ul>
 * Left deliberately unchanged, warts included: {@code bikeId} and {@code renter} are kept as mutable fields exactly
 * as Axon Framework 4 stored them. A redelivered {@link BikeRequested} still starts a second payment for the same
 * rental, precisely as it did in Axon Framework 4 -- a bug the four AF5-native recipes in this module all fix, and
 * this one faithfully reproduces.
 * <p>
 * One dispatch is not fire-and-forget, though, and this is a deliberate departure from the original: the
 * {@code PreparePayment} sent below is joined. Axon Framework 4 retried a failed dispatch through a scheduled
 * deadline; with that commented out (see below), an un-joined dispatch would mean a failed {@code PreparePayment}
 * is simply lost forever, since the event handler returns, {@code void}, before the command resolves, and the event
 * is marked handled regardless of what happens to it afterward. Joining turns that failure into a failure of the
 * event itself, so the processor's own redelivery retries it -- a cruder stand-in for the deadline-based retry, but
 * not nothing. The other three dispatches ({@code ApproveRequest}, {@code RejectRequest}) remain fire-and-forget,
 * matching the original {@code commandGateway.send(...)} calls they replace.
 * <p>
 * {@link StartSaga @StartSaga} is deprecated in {@code axon-legacy}: the module exists to let already-running Axon
 * Framework 4 Sagas finish, not to start new ones. This class uses it anyway, deliberately, because showing what
 * Axon Framework 4 code looked like is the entire point of this package.
 * <p>
 * The {@link JsonCreator @JsonCreator} constructor and the getters below exist for the same reason they did in the
 * original: a JPA-backed {@code SagaStore} round-trips this instance through Jackson between events, and Jackson
 * cannot see private fields without either an accessible constructor or accessors naming them. Axon Framework 4's
 * saga needed the same for the same reason.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@Saga
@ConditionalOnProperty(name = "saga.recipe", havingValue = "legacy")
public class PaymentSaga {

    private BikeId bikeId;
    private String renter;

    /**
     * Default constructor. The framework uses this to create a fresh instance before {@link StartSaga @StartSaga}
     * populates it; {@link #PaymentSaga(BikeId, String)} is what Jackson uses to restore a persisted one.
     */
    public PaymentSaga() {
    }

    @JsonCreator
    private PaymentSaga(@JsonProperty("bikeId") BikeId bikeId, @JsonProperty("renter") String renter) {
        this.bikeId = bikeId;
        this.renter = renter;
    }

    /**
     * Asks for payment as soon as a bike is requested.
     *
     * @param event      the event that started this process
     * @param lifecycle  associates this instance with the payment it is about to ask for
     * @param dispatcher dispatches the resulting command
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "bikeId")
    public void on(BikeRequested event, SagaLifecycle lifecycle, CommandDispatcher dispatcher) {
        this.bikeId = event.bikeId();
        this.renter = event.renter();
        PaymentReference reference = RentalPaymentReference.forRental(event.rentalId());
        lifecycle.associateWith("paymentReference", reference.raw());
        // Joined, unlike the dispatches below: with the deadline-based retry commented out, this is what turns a
        // failed dispatch into a failed event instead of a silently lost one, so the event processor's own
        // redelivery retries it. See the class-level Javadoc.
        dispatcher.send(new PreparePayment(reference, RentalPricing.PRICE))
                  .getResultMessage()
                  .orTimeout(10, TimeUnit.SECONDS)
                  .join();
        // TODO axon-legacy #5006: Axon Framework 4 retried a failed PreparePayment dispatch here through a
        // scheduled "retryPayment" deadline, instead of relying on event redelivery. Reintroduce once deadlines
        // are ported into axon-legacy:
        //
        // ScopeDescriptor scope = Scope.describeCurrentScope();
        // dispatcher.send(new PreparePayment(reference, RentalPricing.PRICE))
        //           .whenComplete((r, e) -> {
        //               if (e != null) {
        //                   deadlineManager.schedule(Duration.ofSeconds(5), "retryPayment", reference, scope);
        //               }
        //           });
    }

    /**
     * Hands over the bike once the payment is in, and ends the process.
     *
     * @param event      the payment that came in
     * @param dispatcher dispatches the resulting command
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "paymentReference")
    public void on(PaymentConfirmed event, CommandDispatcher dispatcher) {
        dispatcher.send(new ApproveRequest(bikeId, renter));
    }

    /**
     * Releases the bike when the payment is refused.
     *
     * @param event      the refusal
     * @param dispatcher dispatches the resulting command
     */
    @SagaEventHandler(associationProperty = "paymentReference")
    public void on(PaymentRejected event, CommandDispatcher dispatcher) {
        dispatcher.send(new RejectRequest(bikeId, renter));
    }

    /**
     * Ends the process when the request is turned down for reasons of its own.
     *
     * @param event the rejection
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "bikeId")
    public void on(RequestRejected event) {
        // TODO axon-legacy #5006: Axon Framework 4 cancelled the "cancelPayment" deadline here, so an
        // already-released bike would not also time out its payment. Reintroduce once deadlines are ported:
        //
        // deadlineManager.cancelAllWithinScope("cancelPayment");
    }

    /**
     * Notes that a payment is now awaiting confirmation.
     *
     * @param event the payment that was set up
     */
    @SagaEventHandler(associationProperty = "paymentReference")
    public void on(PaymentPrepared event) {
        // TODO axon-legacy #5006: Axon Framework 4 scheduled a 30-second "cancelPayment" deadline here, so an
        // unconfirmed payment would time out. Reintroduce once deadlines are ported into axon-legacy:
        //
        // deadlineManager.schedule(Duration.ofSeconds(30), "cancelPayment", event.paymentId());
    }

    // TODO axon-legacy #5006: Axon Framework 4 had a @DeadlineHandler reacting to the "cancelPayment" deadline
    // scheduled above, rejecting the payment that never got confirmed in time. Reintroduce once deadlines are
    // ported into axon-legacy:
    //
    // @DeadlineHandler(deadlineName = "cancelPayment")
    // public void cancelPayment(PaymentId paymentId, CommandDispatcher dispatcher) {
    //     dispatcher.send(new RejectPayment(paymentId));
    // }

    // Getters to satisfy Jackson's serialization requirements, matching the Axon Framework 4 original.

    @SuppressWarnings("unused")
    public BikeId getBikeId() {
        return bikeId;
    }

    @SuppressWarnings("unused")
    public String getRenter() {
        return renter;
    }
}
