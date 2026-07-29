package com.dinehub.controller.admin;

import com.dinehub.dto.DiningTableDTO;
import com.dinehub.dto.DiningTablePageQueryDTO;
import com.dinehub.entity.DiningTable;
import com.dinehub.result.PageResult;
import com.dinehub.result.Result;
import com.dinehub.service.DiningTableService;
import com.dinehub.utils.QrCodeUtil;
import com.dinehub.vo.DiningTableVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 桌台管理:商家维护桌台并生成扫码点单二维码
 */
@RestController
@RequestMapping("/admin/table")
@Api(tags = "桌台相关接口")
@Slf4j
public class DiningTableController {

    @Autowired
    private DiningTableService diningTableService;

    @Value("${dinehub.scan-order.h5-base-url}")
    private String h5BaseUrl;

    @PostMapping
    @ApiOperation("新增桌台")
    public Result<DiningTableVO> save(@RequestBody DiningTableDTO diningTableDTO) {
        log.info("新增桌台:{}", diningTableDTO);
        DiningTable diningTable = diningTableService.save(diningTableDTO);
        return Result.success(toVO(diningTable));
    }

    @GetMapping("/page")
    @ApiOperation("桌台分页查询")
    public Result<PageResult> page(DiningTablePageQueryDTO diningTablePageQueryDTO) {
        return Result.success(diningTableService.pageQuery(diningTablePageQueryDTO));
    }

    @PutMapping
    @ApiOperation("修改桌台")
    public Result update(@RequestBody DiningTableDTO diningTableDTO) {
        diningTableService.update(diningTableDTO);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启用/停用桌台")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        diningTableService.startOrStop(status, id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除桌台")
    public Result deleteById(@PathVariable Long id) {
        diningTableService.deleteById(id);
        return Result.success();
    }

    @GetMapping(value = "/qrcode/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    @ApiOperation("下载桌台点单二维码")
    public ResponseEntity<byte[]> qrcode(@PathVariable Long id) {
        DiningTable diningTable = diningTableService.getById(id);
        byte[] png = QrCodeUtil.toPng(buildOrderUrl(diningTable.getQrToken()), 300);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/qrcode-info/{id}")
    @ApiOperation("查询桌台二维码内容")
    public Result<DiningTableVO> qrcodeInfo(@PathVariable Long id) {
        return Result.success(toVO(diningTableService.getById(id)));
    }

    @PostMapping("/qrcode/{id}/rotate")
    @ApiOperation("换新桌台二维码令牌")
    public Result<DiningTableVO> rotateQrcode(@PathVariable Long id) {
        return Result.success(toVO(diningTableService.rotateQrToken(id)));
    }

    private DiningTableVO toVO(DiningTable diningTable) {
        return DiningTableVO.builder()
                .id(diningTable.getId())
                .name(diningTable.getName())
                .qrToken(diningTable.getQrToken())
                .status(diningTable.getStatus())
                .orderUrl(buildOrderUrl(diningTable.getQrToken()))
                .updateTime(diningTable.getUpdateTime())
                .build();
    }

    private String buildOrderUrl(String qrToken) {
        return h5BaseUrl + "?tableToken=" + qrToken;
    }
}
