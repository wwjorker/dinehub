package com.dinehub.service.impl;

import com.dinehub.constant.MessageConstant;
import com.dinehub.constant.StatusConstant;
import com.dinehub.dto.DiningTableDTO;
import com.dinehub.dto.DiningTablePageQueryDTO;
import com.dinehub.entity.DiningTable;
import com.dinehub.exception.OrderBusinessException;
import com.dinehub.mapper.DiningTableMapper;
import com.dinehub.result.PageResult;
import com.dinehub.service.DiningTableService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class DiningTableServiceImpl implements DiningTableService {

    @Autowired
    private DiningTableMapper diningTableMapper;

    public DiningTable save(DiningTableDTO diningTableDTO) {
        DiningTable diningTable = new DiningTable();
        BeanUtils.copyProperties(diningTableDTO, diningTable);
        // token is server-generated and unguessable; uniqueness backed by uk_dining_table_qr_token
        diningTable.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        diningTable.setStatus(StatusConstant.ENABLE);
        diningTableMapper.insert(diningTable);
        return diningTable;
    }

    public PageResult pageQuery(DiningTablePageQueryDTO diningTablePageQueryDTO) {
        PageHelper.startPage(diningTablePageQueryDTO.getPage(), diningTablePageQueryDTO.getPageSize());
        Page<DiningTable> page = diningTableMapper.pageQuery(diningTablePageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    public void update(DiningTableDTO diningTableDTO) {
        DiningTable diningTable = new DiningTable();
        BeanUtils.copyProperties(diningTableDTO, diningTable);
        diningTableMapper.update(diningTable);
    }

    public void startOrStop(Integer status, Long id) {
        DiningTable diningTable = DiningTable.builder()
                .id(id)
                .status(status)
                .build();
        diningTableMapper.update(diningTable);
    }

    public void deleteById(Long id) {
        diningTableMapper.deleteById(id);
    }

    public DiningTable getById(Long id) {
        DiningTable diningTable = diningTableMapper.getById(id);
        if (diningTable == null) {
            throw new OrderBusinessException(MessageConstant.TABLE_NOT_AVAILABLE);
        }
        return diningTable;
    }

    public DiningTable getAvailableByToken(String qrToken) {
        DiningTable diningTable = diningTableMapper.getByToken(qrToken);
        if (diningTable == null || !StatusConstant.ENABLE.equals(diningTable.getStatus())) {
            throw new OrderBusinessException(MessageConstant.TABLE_NOT_AVAILABLE);
        }
        return diningTable;
    }

    public DiningTable rotateQrToken(Long id) {
        DiningTable diningTable = getById(id);
        diningTable.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        diningTable.setStatus(null);
        diningTableMapper.update(diningTable);
        return getById(id);
    }
}
