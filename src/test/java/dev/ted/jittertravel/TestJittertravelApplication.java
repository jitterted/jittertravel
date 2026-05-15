package dev.ted.jittertravel;

import org.springframework.boot.SpringApplication;

public class TestJittertravelApplication {

    public static void main(String[] args) {
        SpringApplication.from(JittertravelApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
