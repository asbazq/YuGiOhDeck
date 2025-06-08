package com.card.Yugioh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication(exclude = ElasticsearchRepositoriesAutoConfiguration.class)
public class YugiohApplication {

	public static void main(String[] args) {
		SpringApplication.run(YugiohApplication.class, args);
	}
}
	