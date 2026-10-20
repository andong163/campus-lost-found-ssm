package com.team.cms.auth.controller;

import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.dto.CurrentUserResponse;
import com.team.cms.auth.dto.LoginRequest;
import com.team.cms.auth.dto.RegisterRequest;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.auth.security.CurrentUserContext;
import com.team.cms.auth.service.AuthService;
import com.team.cms.common.api.Result;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 身份接口：注册、登录、退出、当前用户查询。基础地址 /cms/api。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 登录成功销毁旧会话并换发新会话标识（防会话固定），身份写入会话属性。 */
    @PostMapping("/login")
    public Result<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        CurrentUser user = authService.login(request);
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(AuthConstants.SESSION_USER, user);
        return Result.ok(CurrentUserResponse.from(user));
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
