package ru.posts.suggest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Suggest autopost wiring (BE-34). Concrete services/handlers are component-scanned via app {@code
 * scanBasePackages = "ru.posts"}; this stub enables the module on the classpath for now.
 */
@AutoConfiguration
@Profile("!openapi")
public class SuggestAutoConfiguration {}
