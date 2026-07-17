package com.elparche.uno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UnoRsApplication {

	public static void main(String[] args) {
		SpringApplication.run(UnoRsApplication.class, args);
	}

}
