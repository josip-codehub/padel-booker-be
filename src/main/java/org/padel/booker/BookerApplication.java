package org.padel.booker;

import org.padel.booker.config.AppAuthProperties;
import org.padel.booker.config.SportyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SportyProperties.class, AppAuthProperties.class})
public class BookerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookerApplication.class, args);
    }
}
