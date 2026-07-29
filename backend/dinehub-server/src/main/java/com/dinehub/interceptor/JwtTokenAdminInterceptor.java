package com.dinehub.interceptor;

import com.dinehub.constant.JwtClaimsConstant;
import com.dinehub.context.BaseContext;
import com.dinehub.properties.JwtProperties;
import com.dinehub.utils.JwtUtil;
import com.dinehub.entity.Employee;
import com.dinehub.mapper.EmployeeMapper;
import com.dinehub.constant.StatusConstant;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //2、校验令牌
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            Employee employee = employeeMapper.getById(empId);
            if (employee == null || !StatusConstant.ENABLE.equals(employee.getStatus())) {
                response.setStatus(401);
                return false;
            }
            String path = request.getRequestURI();
            if (Boolean.TRUE.equals(employee.getMustChangePassword())
                    && !path.endsWith("/admin/employee/password")
                    && !path.endsWith("/admin/employee/logout")) {
                response.setStatus(428);
                return false;
            }
            if (requiresAdmin(path) && !"ADMIN".equals(employee.getRole())) {
                response.setStatus(403);
                return false;
            }
            //令牌是凭据,日志只记录解析出的身份,不落完整token
            log.debug("admin request authenticated, empId={}", empId);
            BaseContext.setCurrentId(empId);
            BaseContext.setCurrentRole(employee.getRole());
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }

    private boolean requiresAdmin(String path) {
        if (path.endsWith("/admin/employee/password") || path.endsWith("/admin/employee/logout")) {
            return false;
        }
        if (path.startsWith("/admin/employee")) {
            return true;
        }
        return path.contains("/rebuild-cache") || path.contains("/reconciliation");
    }

    /**
     * 请求结束清理ThreadLocal,防止线程复用导致的身份串号与内存泄漏
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
