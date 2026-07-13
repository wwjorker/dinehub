package com.dinehub.config;

import com.dinehub.constant.PasswordConstant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Explicitly dev-only demo identity. Production migrations finish without a
 * public administrator, while local Docker remains immediately demonstrable.
 */
@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DevDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.update("insert ignore into employee(name, username, password, phone, sex, id_number," +
                        " status, create_time, update_time, create_user, update_user)" +
                        " values(?, 'admin', ?, '13900000001', '1', '320102199501015678', 1, now(), now(), null, null)",
                "系统管理员", passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
    }
}
