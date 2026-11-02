package com.team.cms.auth.controller;

import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.dto.CurrentUserResponse;
import com.team.cms.auth.dto.LoginRequest;
import com.team.cms.auth.dto.RegisterRequest;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.auth.security.CsrfTokenService;
import com.team.cms.auth.service.AuthService;
import com.team.cms.common.api.Result;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 身份接口：注册、登录、退出、当前用户查询与写请求令牌。基础地址 /cms/api。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CsrfTokenService csrfTokenService;

    public AuthController(AuthService authService, CsrfTokenService csrfTokenService) {
        this.authService = authService;
        this.csrfTokenService = csrfTokenService;
    }

    @PostMapping("/register")
    public Result<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 登录成功销毁旧会话并换发新会话标识（防会话固定），同时换发写请求令牌。 */
    @PostMapping("/login")
    public Result<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        CurrentUser user = authService.login(request);
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        // 登录时写入身份快照；之后每次请求由拦截器按主键重新装载并覆盖，旧权限不会滞留。
        session.setAttribute(AuthConstants.SESSION_USER, user);
        csrfTokenService.issue(session);
        return Result.ok(CurrentUserResponse.from(user));
    }

    /** 登录后由同源页面读取令牌，供物品草稿写请求放入 X-CSRF-Token 请求头。 */
    @GetMapping("/csrf")
    public Result<String> csrf(HttpServletRequest request, HttpServletResponse response) {
        CurrentUserContext.require();
        response.setHeader("Cache-Control", "no-store");
        return Result.ok(csrfTokenService.current(request.getSession(false)));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        CurrentUserContext.clear();
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me() {
        return Result.ok(CurrentUserResponse.from(CurrentUserContext.require()));
    }
}
