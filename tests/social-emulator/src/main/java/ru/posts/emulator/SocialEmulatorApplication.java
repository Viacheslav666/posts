package ru.posts.emulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.posts.emulator.config.EmulatorProperties;

@SpringBootApplication
@EnableConfigurationProperties(EmulatorProperties.class)
public class SocialEmulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialEmulatorApplication.class, args);
    }
}
