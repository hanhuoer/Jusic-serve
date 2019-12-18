package com.scoder.jusic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author H
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class JusicApplication {

    public static void main(String[] args) {
        SpringApplication.run(JusicApplication.class, args);
    }

}
