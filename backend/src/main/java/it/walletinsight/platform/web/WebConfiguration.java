package it.walletinsight.platform.web;

import it.walletinsight.platform.config.MargineProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(MargineProperties.class)
class WebConfiguration implements WebMvcConfigurer {

    private final MargineProperties properties;

    WebConfiguration(MargineProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.allowedOrigins().isEmpty()) {
            return;
        }
        // allowedHeaders("*") e maxAge(1800) sono i valori che Spring applica comunque via
        // CorsConfiguration.applyPermitDefaultValues() prima che questo metodo venga eseguito:
        // renderli espliciti evita di far credere che la configurazione sia più stretta di quanto sia.
        // I metodi di scrittura servono al frontend in sviluppo locale (il dev server Angular
        // è cross-origin); in Docker il proxy nginx rende le chiamate same-origin e il CORS non scatta.
        registry.addMapping(ApiPaths.API + "/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*")
                .maxAge(1800);
    }
}
