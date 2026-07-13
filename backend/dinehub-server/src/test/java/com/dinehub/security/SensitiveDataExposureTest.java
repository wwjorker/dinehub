package com.dinehub.security;

import com.dinehub.dto.EmployeeLoginDTO;
import com.dinehub.entity.Employee;
import com.dinehub.json.JacksonObjectMapper;
import com.dinehub.properties.AliOssProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveDataExposureTest {

    @Test
    void loginDtoToStringDoesNotExposePlaintextPassword() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("operator");
        dto.setPassword("plain-text-secret");

        assertFalse(dto.toString().contains("plain-text-secret"));
    }

    @Test
    void ossPropertiesToStringDoesNotExposeCredentials() {
        AliOssProperties properties = new AliOssProperties();
        properties.setEndpoint("oss.example.com");
        properties.setAccessKeyId("credential-id");
        properties.setAccessKeySecret("credential-secret");

        String rendered = properties.toString();
        assertFalse(rendered.contains("credential-id"));
        assertFalse(rendered.contains("credential-secret"));
    }

    @Test
    void employeeJsonDoesNotExposePasswordHash() throws Exception {
        Employee employee = Employee.builder().id(1L).username("operator")
                .password("bcrypt-hash-that-must-not-leave-api").build();

        String json = new JacksonObjectMapper().writeValueAsString(employee);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("bcrypt-hash-that-must-not-leave-api"));
    }
}
