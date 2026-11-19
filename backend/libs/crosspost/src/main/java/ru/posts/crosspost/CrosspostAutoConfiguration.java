package ru.posts.crosspost;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import ru.posts.crosspost.config.CrosspostProperties;

/**
 * Registers configuration properties. Concrete {@code @Component}/{@code @Service} classes under
 * {@code ru.posts.crosspost} are picked up by apps that scan {@code ru.posts}.
 */
@AutoConfiguration
@Profile("!openapi")
@EnableConfigurationProperties(CrosspostProperties.class)
public class CrosspostAutoConfiguration {}
