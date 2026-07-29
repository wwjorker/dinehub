package com.dinehub.service.impl;

import com.dinehub.entity.User;
import com.dinehub.exception.LoginFailedException;
import com.dinehub.mapper.UserMapper;
import com.dinehub.properties.WeChatProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private WeChatProperties weChatProperties;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void demoLoginUsesDeviceIdentityInsteadOfNickname() {
        userService.demoLogin("小王", "11111111-1111-4111-8111-111111111111");
        userService.demoLogin("小王", "22222222-2222-4222-8222-222222222222");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("小王", captor.getAllValues().get(0).getName());
        assertNotEquals(captor.getAllValues().get(0).getOpenid(),
                captor.getAllValues().get(1).getOpenid());
    }

    @Test
    void demoLoginUpdatesDisplayNameForSameDevice() {
        String deviceId = "11111111-1111-4111-8111-111111111111";
        User existing = User.builder().id(7L).openid("demo:" + deviceId).name("旧昵称").build();
        when(userMapper.getByOpenid("demo:" + deviceId)).thenReturn(existing);

        User result = userService.demoLogin("新昵称", deviceId);

        verify(userMapper).updateName(7L, "新昵称");
        assertEquals("新昵称", result.getName());
    }

    @Test
    void demoLoginRejectsMissingOrShortDeviceIdentity() {
        assertThrows(LoginFailedException.class, () -> userService.demoLogin("小王", null));
        assertThrows(LoginFailedException.class, () -> userService.demoLogin("小王", "short"));
    }
}
