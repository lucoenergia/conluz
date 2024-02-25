package org.lucoenergia.conluz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConLuzApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConLuzApplication.class, args);
    }
}
