package com.dinehub.controller.user;

import com.dinehub.entity.DiningTable;
import com.dinehub.result.Result;
import com.dinehub.service.DiningTableService;
import com.dinehub.vo.DiningTableVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扫码点单:顾客扫码后用令牌换取桌台信息
 */
@RestController("userTableController")
@RequestMapping("/user/table")
@Api(tags = "C端-桌台接口")
@Slf4j
public class TableController {

    @Autowired
    private DiningTableService diningTableService;

    @GetMapping("/info")
    @ApiOperation("根据扫码令牌查询桌台信息")
    public Result<DiningTableVO> info(@RequestParam String tableToken) {
        DiningTable diningTable = diningTableService.getAvailableByToken(tableToken);
        DiningTableVO vo = DiningTableVO.builder()
                .id(diningTable.getId())
                .name(diningTable.getName())
                .status(diningTable.getStatus())
                .build();
        return Result.success(vo);
    }
}
