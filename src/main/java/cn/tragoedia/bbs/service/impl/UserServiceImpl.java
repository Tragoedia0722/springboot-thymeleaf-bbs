package cn.tragoedia.bbs.service.impl;

import cn.tragoedia.bbs.entity.LoginTicket;
import cn.tragoedia.bbs.entity.User;
import cn.tragoedia.bbs.repository.LoginTicketRepository;
import cn.tragoedia.bbs.repository.UserRepository;
import cn.tragoedia.bbs.service.UserService;
import cn.tragoedia.bbs.utils.CommonUtil;
import cn.tragoedia.bbs.utils.Constant;
import cn.tragoedia.bbs.utils.MailClientUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService, Constant {
    @Resource
    private UserRepository userRepository;
    @Resource
    private MailClientUtil mailClientUtil;
    @Resource
    private TemplateEngine templateEngine;
    @Resource
    private LoginTicketRepository loginTicketRepository;

    @Value("${bbs.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Override
    public User findUserById(int id) {
        return userRepository.findUserById(id);
    }

    @Override
    public User findUserByUsername(String username) {
        return userRepository.findUserByUsername(username);
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findUserByEmail(email);
    }

    @Override
    public User insertUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public int updateStatusById(int id, int status) {
        return userRepository.updateStatusById(id, status);
    }

    @Override
    public int updateHeaderById(int id, String headerUrl) {
        return userRepository.updateHeaderById(id, headerUrl);
    }

    @Override
    public int updatePasswordById(int id, String password) {
        return userRepository.updatePasswordById(id, password);
    }

    @Override
    public Map<String, Object> register(User user) {
        Map<String, Object> registerMap = new HashMap<>();
        // ????????????
        if (user == null) {
            throw new IllegalArgumentException("??????????????????");
        }
        if (StringUtils.isBlank(user.getUsername())) {
            registerMap.put("usernameMsg", "??????????????????");
            return registerMap;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            registerMap.put("passwordMsg", "??????????????????");
            return registerMap;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            registerMap.put("emailMsg", "??????????????????");
            return registerMap;
        }
        // ????????????
        User userByUsername = userRepository.findUserByUsername(user.getUsername());
        if (userByUsername != null) {
            registerMap.put("usernameMsg", "??????????????????");
            return registerMap;
        }
        User userByEmail = userRepository.findUserByEmail(user.getEmail());
        if (userByEmail != null) {
            registerMap.put("emailMsg", "?????????????????????");
            return registerMap;
        }
        // ??????
        user.setSalt(CommonUtil.generateUUID().substring(0, 5));
        user.setPassword(CommonUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(CommonUtil.generateUUID());
        user.setHeaderUrl("http://images.nowcoder.com/head/1t.png");
        user.setCreateTime(new Date());
        userRepository.save(user);
        // ????????????
        Context context = new Context();
        context.setVariable("email", user.getEmail());
        String url = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
        context.setVariable("url", url);
        String content = templateEngine.process("/mail/activation", context);
        mailClientUtil.sendMail(user.getEmail(), "????????????", content);
        return registerMap;
    }

    @Override
    public int activation(int id, String code) {
        User user = userRepository.findUserById(id);
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;
        } else if (user.getActivationCode().equals(code)) {
            userRepository.updateStatusById(id, 1);
            return ACTIVATION_SUCCESS;
        } else {
            return ACTIVATION_FAILURE;
        }
    }

    @Override
    public Map<String, Object> login(String username, String password, int expired) {
        Map<String, Object> map = new HashMap<>();
        // ????????????
        if (StringUtils.isBlank(username)) {
            map.put("usernameMsg", "??????????????????");
            return map;
        }
        if (StringUtils.isBlank(password)) {
            map.put("passwordMsg", "??????????????????");
            return map;
        }
        // ????????????
        User user = userRepository.findUserByUsername(username);
        if (user == null) {
            map.put("usernameMsg", "???????????????");
            return map;
        }
        // ????????????
        if (user.getStatus() == 0) {
            map.put("usernameMsg", "???????????????");
            return map;
        }
        // ????????????
        if (!user.getPassword().equals(CommonUtil.md5(password + user.getSalt()))) {
            map.put("passwordMsg", "????????????");
            return map;
        }
        // ????????????
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        loginTicket.setTicket(CommonUtil.generateUUID());
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis() + expired * 1000L));
        loginTicketRepository.save(loginTicket);

        map.put("ticket", loginTicket.getTicket());
        return map;
    }

    @Override
    public void logout(String ticket) {
        loginTicketRepository.updateStatusByTicket(ticket, 1);
    }

    @Override
    public LoginTicket findLoginTicket(String value) {
        return loginTicketRepository.findLoginTicketByTicket(value);
    }

    @Override
    public int updateHeader(int userId, String headerUrl) {
        return userRepository.updateHeaderById(userId, headerUrl);
    }

    public Map<String, Object> updatePassword(int userId, String oldPassword, String newPassword) {
        Map<String, Object> map = new HashMap<>();

        // ????????????
        if (StringUtils.isBlank(oldPassword)) {
            map.put("oldPasswordMsg", "?????????????????????!");
            return map;
        }
        if (StringUtils.isBlank(newPassword)) {
            map.put("newPasswordMsg", "?????????????????????!");
            return map;
        }

        // ??????????????????
        User user = userRepository.findUserById(userId);
        oldPassword = CommonUtil.md5(oldPassword + user.getSalt());
        if (!user.getPassword().equals(oldPassword)) {
            map.put("oldPasswordMsg", "?????????????????????!");
            return map;
        }

        // ????????????
        newPassword = CommonUtil.md5(newPassword + user.getSalt());
        userRepository.updatePasswordById(userId, newPassword);

        return map;
    }

    // ????????????
    public Map<String, Object> resetPassword(String email, String password) {
        Map<String, Object> map = new HashMap<>();

        // ????????????
        if (StringUtils.isBlank(email)) {
            map.put("emailMsg", "??????????????????!");
            return map;
        }
        if (StringUtils.isBlank(password)) {
            map.put("passwordMsg", "??????????????????!");
            return map;
        }

        // ????????????
        User user = userRepository.findUserByEmail(email);
        if (user == null) {
            map.put("emailMsg", "?????????????????????!");
            return map;
        }

        // ????????????
        password = CommonUtil.md5(password + user.getSalt());
        userRepository.updatePasswordById(user.getId(), password);

        map.put("user", user);
        return map;
    }

    @Override
    public Map<String, Object> getForgetCode(String email) {
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isBlank(email)) {
            map.put("emailMsg", "??????????????????!");
            return map;
        }

        // ????????????
        Context context = new Context();
        context.setVariable("email", email);
        String code = CommonUtil.generateUUID().substring(0, 4);
        context.setVariable("verifyCode", code);
        String content = templateEngine.process("/mail/forget", context);
        mailClientUtil.sendMail(email, "????????????", content);

        map.put("code", code);
        return map;
    }
}
