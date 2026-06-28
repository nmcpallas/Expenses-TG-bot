package com.cpallas.expenses;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ExpensesTgBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpensesTgBotApplication.class, args);
	}

}
