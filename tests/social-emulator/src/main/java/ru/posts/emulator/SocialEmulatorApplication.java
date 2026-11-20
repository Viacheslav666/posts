package ru.posts.emulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.posts.emulator.config.EmulatorProperties;
import ru.posts.emulator.connect.EmulatorConnectProperties;

@SpringBootApplication
@EnableConfigurationProperties({EmulatorProperties.class, EmulatorConnectProperties.class})
public class SocialEmulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialEmulatorApplication.class, args);
    }
}
