package com.questionbank.service;

import com.questionbank.auth.CurrentUser;
import com.questionbank.auth.JwtUtil;
import com.questionbank.auth.UserContext;
import com.questionbank.common.BizException;
import com.questionbank.common.enums.UserRole;
import com.questionbank.dto.AuthDtos.LoginRequest;
import com.questionbank.dto.AuthDtos.LoginResponse;
import com.questionbank.dto.AuthDtos.UserView;
import com.questionbank.entity.SysUser;
import com.questionbank.repository.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AuthService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        SysUser user = userRepository.findByUsername(req.username().trim())
                .orElseThrow(() -> BizException.unauthorized("用户名或密码错误"));
        if (!user.isEnabled()) {
            throw BizException.unauthorized("账号已被停用, 请联系管理员");
        }
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        CurrentUser cu = new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        String token = jwtUtil.createToken(cu);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.getRole().getLabel());
    }

    @Transactional(readOnly = true)
    public UserView me() {
        CurrentUser cu = UserContext.required();
        SysUser user = userRepository.findById(cu.id())
                .orElseThrow(() -> BizException.unauthorized("用户不存在"));
        return toView(user);
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        CurrentUser cu = UserContext.required();
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw BizException.bad("新密码至少 6 位");
        }
        SysUser user = userRepository.findById(cu.id())
                .orElseThrow(() -> BizException.unauthorized("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw BizException.bad("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers() {
        UserContext.requireRole(UserRole.ADMIN);
        return userRepository.findAll().stream().map(this::toView).toList();
    }

    private UserView toView(SysUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(),
                u.getRole().getLabel(), u.isEnabled(), u.getCreatedAt());
    }
}
