package com.dinehub.service.impl;

import com.dinehub.dto.EmployeeLoginDTO;
import com.dinehub.entity.Employee;
import com.dinehub.exception.PasswordErrorException;
import com.dinehub.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private EmployeeLoginDTO dto(String username, String password) {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private Employee employee(String storedHash) {
        return Employee.builder().id(1L).username("admin").password(storedHash).status(1).build();
    }

    @Test
    void bcryptAccountLogsInWithoutUpgrade() {
        String hash = new BCryptPasswordEncoder().encode("123456");
        when(employeeMapper.getByUsername("admin")).thenReturn(employee(hash));

        assertNotNull(employeeService.login(dto("admin", "123456")));
        verify(employeeMapper, never()).upgradePassword(anyLong(), anyString(), anyString());
    }

    @Test
    void legacyMd5AccountUpgradesOnSuccessfulLogin() {
        String md5 = DigestUtils.md5DigestAsHex("123456".getBytes());
        when(employeeMapper.getByUsername("admin")).thenReturn(employee(md5));

        assertNotNull(employeeService.login(dto("admin", "123456")));
        //条件升级:旧哈希作为where条件,并发登录只有一方生效
        verify(employeeMapper).upgradePassword(eq(1L), eq(md5), anyString());
    }

    @Test
    void wrongPasswordNeverTriggersUpgrade() {
        String md5 = DigestUtils.md5DigestAsHex("123456".getBytes());
        when(employeeMapper.getByUsername("admin")).thenReturn(employee(md5));

        assertThrows(PasswordErrorException.class, () -> employeeService.login(dto("admin", "wrong")));
        verify(employeeMapper, never()).upgradePassword(anyLong(), anyString(), anyString());
    }

    @Test
    void wrongPasswordOnBcryptAccountRejected() {
        String hash = new BCryptPasswordEncoder().encode("123456");
        when(employeeMapper.getByUsername("admin")).thenReturn(employee(hash));

        assertThrows(PasswordErrorException.class, () -> employeeService.login(dto("admin", "wrong")));
    }
}
