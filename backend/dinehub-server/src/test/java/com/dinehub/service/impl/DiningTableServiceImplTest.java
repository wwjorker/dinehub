package com.dinehub.service.impl;

import com.dinehub.constant.StatusConstant;
import com.dinehub.dto.DiningTableDTO;
import com.dinehub.entity.DiningTable;
import com.dinehub.exception.OrderBusinessException;
import com.dinehub.mapper.DiningTableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiningTableServiceImplTest {

    @Mock
    private DiningTableMapper diningTableMapper;

    @InjectMocks
    private DiningTableServiceImpl diningTableService;

    @Test
    void saveGeneratesTokenAndEnablesTable() {
        DiningTableDTO dto = new DiningTableDTO();
        dto.setName("A01");

        diningTableService.save(dto);

        ArgumentCaptor<DiningTable> captor = ArgumentCaptor.forClass(DiningTable.class);
        verify(diningTableMapper).insert(captor.capture());
        DiningTable saved = captor.getValue();
        assertEquals("A01", saved.getName());
        assertEquals(StatusConstant.ENABLE, saved.getStatus());
        assertNotNull(saved.getQrToken());
        assertEquals(32, saved.getQrToken().length());
    }

    @Test
    void tokensAreUniquePerTable() {
        DiningTableDTO dto = new DiningTableDTO();
        dto.setName("A01");
        String first = diningTableService.save(dto).getQrToken();
        String second = diningTableService.save(dto).getQrToken();
        assertNotEquals(first, second);
    }

    @Test
    void getAvailableByTokenReturnsEnabledTable() {
        DiningTable table = DiningTable.builder().id(5L).name("A05").status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getByToken("tok")).thenReturn(table);

        assertEquals(5L, diningTableService.getAvailableByToken("tok").getId());
    }

    @Test
    void getAvailableByTokenRejectsUnknownToken() {
        when(diningTableMapper.getByToken(any())).thenReturn(null);

        assertThrows(OrderBusinessException.class, () -> diningTableService.getAvailableByToken("missing"));
    }

    @Test
    void getAvailableByTokenRejectsDisabledTable() {
        DiningTable table = DiningTable.builder().id(5L).status(StatusConstant.DISABLE).build();
        when(diningTableMapper.getByToken("tok")).thenReturn(table);

        assertThrows(OrderBusinessException.class, () -> diningTableService.getAvailableByToken("tok"));
    }

    @Test
    void rotateQrTokenInvalidatesPreviousPrintedValue() {
        DiningTable stored = DiningTable.builder()
                .id(5L).name("A05").qrToken("old-token").status(StatusConstant.ENABLE).build();
        when(diningTableMapper.getById(5L)).thenReturn(stored);

        DiningTable result = diningTableService.rotateQrToken(5L);

        ArgumentCaptor<DiningTable> captor = ArgumentCaptor.forClass(DiningTable.class);
        verify(diningTableMapper).update(captor.capture());
        assertNotEquals("old-token", captor.getValue().getQrToken());
        assertEquals(32, captor.getValue().getQrToken().length());
        assertNotNull(result);
    }
}
