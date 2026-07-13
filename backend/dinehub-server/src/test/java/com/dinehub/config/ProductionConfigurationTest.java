package com.dinehub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionConfigurationTest {

    @Test
    void productionMqCredentialsHaveNoFallback() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));
        PropertySource<?> prod = sources.get(0);

        assertEquals("${DINEHUB_MQ_USERNAME}", prod.getProperty("spring.rabbitmq.username"));
        assertEquals("${DINEHUB_MQ_PASSWORD}", prod.getProperty("spring.rabbitmq.password"));
    }
}
