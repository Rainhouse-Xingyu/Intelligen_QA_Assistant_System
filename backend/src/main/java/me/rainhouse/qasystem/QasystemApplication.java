package me.rainhouse.qasystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class QasystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(QasystemApplication.class, args);
	}

}
