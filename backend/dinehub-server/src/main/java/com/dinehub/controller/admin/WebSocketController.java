package com.dinehub.controller.admin;

import com.dinehub.context.BaseContext;
import com.dinehub.result.Result;
import com.dinehub.service.WebSocketTicketService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端实时订单通道凭据。
 */
@RestController
@RequestMapping("/admin/ws")
@Api(tags = "管理端实时通知")
public class WebSocketController {

    @Autowired
    private WebSocketTicketService ticketService;

    @GetMapping("/ticket")
    @ApiOperation("签发一次性 WebSocket ticket")
    public Result<String> ticket() {
        return Result.success(ticketService.issue(BaseContext.getCurrentId()));
    }
}
