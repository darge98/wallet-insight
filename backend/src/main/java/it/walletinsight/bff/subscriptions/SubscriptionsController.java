package it.walletinsight.bff.subscriptions;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.subscriptions.application.SubscriptionService;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Gli abbonamenti: definirli, e vedere quanto costano e cosa sta per essere addebitato. */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/subscriptions")
@Tag(name = "Subscriptions")
class SubscriptionsController {

    private final SubscriptionService subscriptions;

    SubscriptionsController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Prima gli attivi, dal prossimo addebito; poi i finiti, dal più recente. */
    @GetMapping
    List<SubscriptionResponse> listSubscriptions(@PathVariable UUID userId) {
        UserId id = UserId.of(userId);
        LocalDate oggi = subscriptions.today(id);
        return subscriptions.listSubscriptions(id).stream()
                .map(abbonamento -> SubscriptionResponse.from(abbonamento, oggi))
                .sorted(Comparator.comparing(SubscriptionResponse::active).reversed()
                        .thenComparing(SubscriptionResponse::nextChargeDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SubscriptionResponse::endDate,
                                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
                .toList();
    }

    @GetMapping("/overview")
    SubscriptionsOverviewResponse overview(
            @PathVariable UUID userId, @RequestParam(defaultValue = "30") int days) {
        return SubscriptionsOverviewResponse.from(subscriptions.overview(UserId.of(userId), days));
    }

    /** Gli addebiti di un mese di calendario, anche passato: il calendario dei rinnovi. */
    @GetMapping("/calendar")
    MonthChargesResponse calendar(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return MonthChargesResponse.from(subscriptions.monthCharges(UserId.of(userId), month));
    }

    @PostMapping
    ResponseEntity<SubscriptionResponse> createSubscription(
            @PathVariable UUID userId, @Valid @RequestBody SubscriptionRequest request) {
        UserId id = UserId.of(userId);
        Subscription creato = subscriptions.createSubscription(id, request.toDefinition());
        URI location = URI.create(ApiPaths.API + "/users/" + userId + "/subscriptions/" + creato.id());
        return ResponseEntity.created(location)
                .body(SubscriptionResponse.from(creato, subscriptions.today(id)));
    }

    @PutMapping("/{subscriptionId}")
    SubscriptionResponse updateSubscription(
            @PathVariable UUID userId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody SubscriptionRequest request) {
        UserId id = UserId.of(userId);
        Subscription aggiornato = subscriptions.updateSubscription(
                id, SubscriptionId.of(subscriptionId), request.toDefinition());
        return SubscriptionResponse.from(aggiornato, subscriptions.today(id));
    }

    @DeleteMapping("/{subscriptionId}")
    ResponseEntity<Void> deleteSubscription(@PathVariable UUID userId, @PathVariable UUID subscriptionId) {
        subscriptions.deleteSubscription(UserId.of(userId), SubscriptionId.of(subscriptionId));
        return ResponseEntity.noContent().build();
    }
}
