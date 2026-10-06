package it.walletinsight.core.ingestion.infrastructure.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Il cifrario è un bean del solo modulo `ingestion`, l'unico che custodisce
 * segreti: salirà in un package condiviso quando nascerà un secondo consumatore.
 *
 * La chiave viene validata costruendo il bean, quindi un'istanza configurata male
 * non parte affatto invece di scoprirlo alla prima scrittura.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EncryptionProperties.class)
class CryptoConfiguration {

    @Bean
    SecretCipher secretCipher(EncryptionProperties properties) {
        return new SecretCipher(properties.key());
    }
}
