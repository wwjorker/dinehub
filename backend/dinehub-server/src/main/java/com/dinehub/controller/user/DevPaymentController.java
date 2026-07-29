package com.dinehub.controller.user;

import com.dinehub.result.Result;
import com.dinehub.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local showcase payment entry. It is deliberately absent outside the dev
 * profile so a production deployment can never accept a simulated payment.
 */
@RestController
@RequestMapping("/user/order")
@Profile("dev")
@Api(tags = "开发环境模拟支付")
public class DevPaymentController {

    private final OrderService orderService;

    public DevPaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PutMapping("/demo-payment/{id}")
    @ApiOperation("模拟支付成功")
    public Result<Void> demoPay(@PathVariable Long id) {
        orderService.demoPay(id);
        return Result.success();
    }
}
