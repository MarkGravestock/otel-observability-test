package org.example.observabilitytest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
public class ObservabilityTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityTestApplication.class, args);
    }

}
