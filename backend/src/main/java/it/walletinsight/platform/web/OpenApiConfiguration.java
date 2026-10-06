package it.walletinsight.platform.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Descrizione minima esposta da springdoc su `/v3/api-docs` e `/swagger-ui.html`:
 * a chi scrive gli adapter HTTP del frontend Angular basta sapere cosa implementa
 * l'API e con quali convenzioni.
 */
@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI margineOpenApi() {
        return new OpenAPI()
                // Server relativo, non l'URL assoluto che springdoc dedurrebbe dalla
                // richiesta: "Try it out" chiama cosi' l'origine da cui la pagina e'
                // servita, qualunque essa sia. Con l'URL assoluto basta aprire la UI da
                // un hostname scritto diverso (127.0.0.1 invece di localhost, un dominio
                // .orb.local, https invece di http) perche' le chiamate partano verso
                // un'altra origine e il CORS le rifiuti. La risposta non e' allargare le
                // origini ammesse per far contenta la documentazione: e' non uscire
                // dall'origine corrente.
                .servers(List.of(new Server().url("/").description("L'origine di questa pagina")))
                .info(new Info()
                        .title("Wallet Insights API")
                        .version("1.0.0")
                        .description("""
                                Implementa via HTTP le porte del dominio dichiarate in \
                                frontend/libs/shared/domain/src/lib/ports/ e consumate dal frontend Angular. \
                                Gli importi sono interi in centesimi, le date senza fuso (yyyy-MM-dd), \
                                le enum in kebab-case e gli errori ProblemDetail (RFC 9457). \
                                L'importazione dalle sorgenti esterne gira qui dentro: \
                                `POST /api/users/{id}/imports` e' "aggiorna ora" premuto \
                                dall'interfaccia."""));
    }
}
