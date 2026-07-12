package com.dinehub.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Springfox 3 cannot inspect the PathPattern-based handler mappings contributed
 * by Spring Boot Actuator. Keep its view limited to legacy MVC mappings while
 * Actuator continues to serve its own endpoints normally.
 */
@Configuration
public class SpringfoxCompatibilityConfiguration {

    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    List<RequestMappingInfoHandlerMapping> mappings = handlerMappings(bean);
                    List<RequestMappingInfoHandlerMapping> compatible = new ArrayList<>();
                    for (RequestMappingInfoHandlerMapping mapping : mappings) {
                        if (mapping.getPatternParser() == null) {
                            compatible.add(mapping);
                        }
                    }
                    mappings.clear();
                    mappings.addAll(compatible);
                }
                return bean;
            }

            @SuppressWarnings("unchecked")
            private List<RequestMappingInfoHandlerMapping> handlerMappings(Object bean) {
                Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                if (field == null) {
                    throw new IllegalStateException("Springfox handlerMappings field not found");
                }
                ReflectionUtils.makeAccessible(field);
                return (List<RequestMappingInfoHandlerMapping>) ReflectionUtils.getField(field, bean);
            }
        };
    }
}
