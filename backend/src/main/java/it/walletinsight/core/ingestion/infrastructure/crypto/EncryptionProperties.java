package it.walletinsight.core.ingestion.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La chiave con cui vengono cifrati i segreti delle connessioni.
 *
 * Sta nell'ambiente (`ENCRYPTION_KEY`) e non nel repository: ogni
 * istanza genera la propria, così un database sottratto non basta a leggere i
 * token e la compromissione di un ambiente non li scopre tutti.
 */
@ConfigurationProperties("margine.encryption")
record EncryptionProperties(String key) {
}
