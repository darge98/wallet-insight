plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
	id("org.sonarqube") version "7.5.0.8588"
}

group = "it.walletinsight"
version = "1.0.0"
description = "API del financial tracker Wallet Insights"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-docs")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.assertj:assertj-core")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

jacoco {
	// Java 25 lo legge solo da JaCoCo 0.8.14 in su.
	toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
	}
}

sonar {
	properties {
		property("sonar.host.url", "https://sonarcloud.io")
		property("sonar.organization", "darge98")
		property("sonar.projectKey", "darge98_wallet-insight-backend")
		property("sonar.projectName", "Wallet Insights — backend")

		// Eccezioni dichiarate, ognuna col suo perché.
		val eccezioni = mapOf(
			// I frammenti dentro `formatted` sono costanti, uno switch su un enum e
			// condizioni fisse: i valori dell'utente passano sempre come parametri.
			"sqlComposto" to ("java:S2077" to "**/movements/infrastructure/jdbc/JdbcMovementRepository.java"),
			// Nomi di parametri e di colonne SQL: una costante per `"userId"` renderebbe
			// la query meno leggibile senza toglierne la ripetizione.
			"parametriSql" to ("java:S1192" to "**/infrastructure/jdbc/**"),
			// La tabella delle categorie di BudgetBakers: il codice ripetuto è il dato.
			"tabellaCategorie" to ("java:S1192" to "**/budgetbakers/BudgetBakersCategories.java"),
			// Nei test l'argomento di assertThatThrownBy si costruisce dentro la lambda:
			// spezzarlo allungherebbe ogni test senza renderlo più preciso.
			"lambdaNeiTest" to ("java:S5778" to "src/test/**"),
			// now() di PostgreSQL è l'ora della transazione: senza attesa created_at e
			// updated_at coinciderebbero anche quando l'update funziona.
			"attesaNeiTest" to ("java:S2925" to "src/test/**"),
		)
		property("sonar.issue.ignore.multicriteria", eccezioni.keys.joinToString(","))
		eccezioni.forEach { (nome, regola) ->
			property("sonar.issue.ignore.multicriteria.$nome.ruleKey", regola.first)
			property("sonar.issue.ignore.multicriteria.$nome.resourceKey", regola.second)
		}
	}
}
