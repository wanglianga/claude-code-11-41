package com.scf.meta.common;

import com.scf.meta.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 认证与角色拦截：除登录外均需 Bearer 令牌；VIEWER 仅允许只读请求。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "scf.user";
    public static final String ATTR_ROLE = "scf.role";

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        TokenService.Session session = tokenService.authenticate(token);
        if (session == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":false,\"message\":\"未认证或令牌已过期\"}");
            return false;
        }
        boolean write = !HttpMethod.GET.matches(request.getMethod());
        if (write && !"ADMIN".equals(session.role())) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":false,\"message\":\"只读账号无权执行写操作\"}");
            return false;
        }
        request.setAttribute(ATTR_USER, session.username());
        request.setAttribute(ATTR_ROLE, session.role());
        return true;
    }
}
