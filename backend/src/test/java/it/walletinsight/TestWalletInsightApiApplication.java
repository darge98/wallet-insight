package it.walletinsight;

import org.springframework.boot.SpringApplication;

public class TestWalletInsightApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(WalletInsightApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
