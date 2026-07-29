package com.dinehub.service.impl;

import com.dinehub.dto.EmployeeLoginDTO;
import com.dinehub.dto.EmployeeDTO;
import com.dinehub.dto.PasswordEditDTO;
import com.dinehub.entity.Employee;
import com.dinehub.exception.PasswordErrorException;
import com.dinehub.exception.BaseException;
import com.dinehub.mapper.EmployeeMapper;
import com.dinehub.context.BaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
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

    @Test
    void newEmployeeUsesChosenTemporaryPasswordAndOperatorRole() {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setUsername("operator");
        dto.setName("运营员");
        dto.setPhone("13900000002");
        dto.setSex("1");
        dto.setIdNumber("320102199501015679");
        dto.setPassword("TempPass88");
        dto.setRole("OPERATOR");

        employeeService.save(dto);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).insert(captor.capture());
        Employee saved = captor.getValue();
        assertEquals("OPERATOR", saved.getRole());
        assertEquals(Boolean.TRUE, saved.getMustChangePassword());
        assertFalse("TempPass88".equals(saved.getPassword()));
        assertEquals(true, new BCryptPasswordEncoder().matches("TempPass88", saved.getPassword()));
    }

    @Test
    void employeeCannotDisableOwnAccount() {
        BaseContext.setCurrentId(9L);
        assertThrows(BaseException.class, () -> employeeService.startOrStop(0, 9L));
        verify(employeeMapper, never()).update(any(Employee.class));
    }

    @Test
    void changingPasswordClearsFirstLoginFlag() {
        BaseContext.setCurrentId(1L);
        String hash = new BCryptPasswordEncoder().encode("TempPass88");
        when(employeeMapper.getById(1L)).thenReturn(employee(hash));
        PasswordEditDTO dto = new PasswordEditDTO();
        dto.setOldPassword("TempPass88");
        dto.setNewPassword("NewPass998");
        dto.setConfirmPassword("NewPass998");

        employeeService.changePassword(dto);

        verify(employeeMapper).updatePassword(eq(1L), anyString(), eq(false));
    }
}
