package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Costruisce il client HTTP e il lettore JSON per BudgetBakers. */
@Configuration
@EnableConfigurationProperties(BudgetBakersProperties.class)
class BudgetBakersHttpConfiguration {

    /**
     * Senza limiti una sorgente che accetta la connessione e non risponde blocca per
     * sempre il thread della schedulazione. Un import completo ne costa ~3 s in tutto,
     * su più pagine: 30 s per una sola risposta sono un margine, non un rischio.
     */
    private static final Duration CONNESSIONE = Duration.ofSeconds(10);
    private static final Duration RISPOSTA = Duration.ofSeconds(30);

    /**
     * Il client non porta credenziali.
     *
     * L'`Authorization` non e' un header predefinito perche' il token cambia a
     * ogni utente: metterlo qui vorrebbe dire un client per connessione, o —
     * peggio — un solo token buono per tutti. Lo aggiunge la singola chiamata.
     */
    @Bean
    RestClient budgetBakersRestClient(BudgetBakersProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNESSIONE).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(RISPOSTA);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /**
     * Un lettore dedicato a questa sorgente, non condiviso con il resto
     * dell'applicazione.
     *
     * Ignora le proprietà che non conosciamo perché la specifica dichiara
     * campi - `convertedAmount`, `photos`, `place` - che non compaiono in
     * nessun movimento e che non ci servono: un campo in più non deve far
     * fallire un import.
     */
    @Bean
    ObjectMapper budgetBakersObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
    }
}
