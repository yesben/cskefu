/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.handler;

import com.chatopera.cc.acd.AutomaticServiceDist;
import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.cache.Cache;
import com.chatopera.cc.model.*;
import com.chatopera.cc.persistence.repository.AgentStatusRepository;
import com.chatopera.cc.persistence.repository.RoleAuthRepository;
import com.chatopera.cc.persistence.repository.UserRepository;
import com.chatopera.cc.persistence.repository.UserRoleRepository;
import com.chatopera.cc.proxy.AgentProxy;
import com.chatopera.cc.proxy.AgentSessionProxy;
import com.chatopera.cc.proxy.OnlineUserProxy;
import com.chatopera.cc.proxy.UserProxy;
import com.chatopera.cc.util.Menu;
import org.apache.commons.lang.StringUtils;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author CSKefu
 * @version 1.0.1
 */
@Controller
public class LoginController extends Handler {
    private final static Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRes;

    @Autowired
    private RoleAuthRepository roleAuthRes;

    @Autowired
    private AgentStatusRepository agentStatusRes;

    @Autowired
    private Cache cache;

    @Autowired
    private AgentProxy agentProxy;

    @Autowired
    private AgentSessionProxy agentSessionProxy;

    /**
     * 登录页面
     *
     * @param request
     * @param response
     * @param referer
     * @param msg
     * @return
     * @throws NoSuchAlgorithmException
     */
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView login(HttpServletRequest request, HttpServletResponse response, @RequestHeader(value = "referer", required = false) String referer, @Valid String msg) {
        ModelAndView view = new ModelAndView("redirect:/");
        if (request.getSession(true).getAttribute(Constants.USER_SESSION_NAME) == null) {
            view = new ModelAndView("/login");
            if (StringUtils.isNotBlank(request.getParameter("referer"))) {
                referer = request.getParameter("referer");
            }
            if (StringUtils.isNotBlank(referer)) {
                view.addObject("referer", referer);
            }
            Cookie[] cookies = request.getCookies(); // 这样便可以获取一个cookie数组
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie != null && StringUtils.isNotBlank(cookie.getName()) && StringUtils.isNotBlank(
                            cookie.getValue())) {
                        if (cookie.getName().equals(Constants.CSKEFU_SYSTEM_COOKIES_FLAG)) {
                            String flagid;
                            try {
                                flagid = MainUtils.decryption(cookie.getValue());
                                if (StringUtils.isNotBlank(flagid)) {
                                    User user = userRepository.findById(flagid);
                                    if (user != null) {
                                        view = this.processLogin(request, user, referer);
                                    }
                                }
                            } catch (EncryptionOperationNotPossibleException e) {
                                logger.error("[login] error:", e);
                                view = request(super.createRequestPageTempletResponse("/public/clearcookie"));
                                return view;
                            } catch (NoSuchAlgorithmException e) {
                                logger.error("[login] error:", e);
                            }
                        }
                    }
                }
            }
        }
        if (StringUtils.isNotBlank(msg)) {
            view.addObject("msg", msg);
        }
        SystemConfig systemConfig = MainUtils.getSystemConfig();
        if (systemConfig != null && systemConfig.isEnableregorgi()) {
            view.addObject("show", true);
        }
        if (systemConfig != null) {
            view.addObject("systemConfig", systemConfig);
        }
        return view;
    }

    /**
     * 提交登录表单
     *
     * @param request
     * @param response
     * @param user
     * @param referer
     * @param sla
     * @return
     * @throws NoSuchAlgorithmException
     */
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView login(
            final HttpServletRequest request,
            final HttpServletResponse response,
            @Valid User user,
            @Valid String referer,
            @Valid String sla) throws NoSuchAlgorithmException {
        ModelAndView view = new ModelAndView("redirect:/");
        if (request.getSession(true).getAttribute(Constants.USER_SESSION_NAME) == null) {
            if (user != null && user.getUsername() != null) {
                final User loginUser = userRepository.findByUsernameAndPasswordAndDatastatus(
                        user.getUsername(), MainUtils.md5(user.getPassword()), false);
                if (loginUser != null && StringUtils.isNotBlank(loginUser.getId())) {
                    view = this.processLogin(request, loginUser, referer);

                    // 自动登录
                    if (StringUtils.equals("1", sla)) {
                        Cookie flagid = new Cookie(
                                Constants.CSKEFU_SYSTEM_COOKIES_FLAG, MainUtils.encryption(loginUser.getId()));
                        flagid.setMaxAge(7 * 24 * 60 * 60);
                        response.addCookie(flagid);
                    }

                    // add authorization code for rest api
                    final String orgi = loginUser.getOrgi();
                    String auth = MainUtils.getUUID();
                    cache.putLoginUserByAuthAndOrgi(auth, loginUser, orgi);
                    userRepository.save(loginUser); // 更新登录状态到数据库
                    response.addCookie((new Cookie("authorization", auth)));

                    // 该登录用户是坐席，并且具有坐席对话的角色
                    if ((loginUser.isAgent() &&
                            loginUser.getRoleAuthMap().containsKey("A01") &&
                            ((boolean) loginUser.getRoleAuthMap().get("A01") == true))
                            || loginUser.isSuperuser()) {
                        try {
                            /****************************************
                             * 登录成功，设置该坐席为就绪状态（默认）
                             ****************************************/
                            // https://gitlab.chatopera.com/chatopera/cosinee.w4l/issues/306
                            final AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentnoAndOrgi(
                                    loginUser.getId(), orgi, loginUser.getSkills());
                            agentStatus.setBusy(false);
                            agentProxy.ready(loginUser, agentStatus);

                            // 更新缓存和数据库
                            cache.putAgentStatusByOrgi(agentStatus, loginUser.getOrgi());
                            agentStatusRes.save(agentStatus);

                            // 工作状态记录
                            AutomaticServiceDist.recordAgentStatus(agentStatus.getAgentno(),
                                                                   agentStatus.getUsername(),
                                                                   agentStatus.getAgentno(),
                                                                   user.isSuperuser(), // 0代表admin
                                                                   agentStatus.getAgentno(),
                                                                   MainContext.AgentStatusEnum.OFFLINE.toString(),
                                                                   MainContext.AgentStatusEnum.READY.toString(),
                                                                   MainContext.AgentWorkType.MEIDIACHAT.toString(),
                                                                   orgi, null);

                        } catch (Exception e) {
                            logger.error("[login] set agent status", e);
                        }
                    }
                } else {
                    view = request(super.createRequestPageTempletResponse("/login"));
                    if (StringUtils.isNotBlank(referer)) {
                        view.addObject("referer", referer);
                    }
                    view.addObject("msg", "0");
                }
            }
        }
        SystemConfig systemConfig = MainUtils.getSystemConfig();
        if (systemConfig != null && systemConfig.isEnableregorgi()) {
            view.addObject("show", true);
        }
        if (systemConfig != null) {
            view.addObject("systemConfig", systemConfig);
        }

        return view;
    }

    /**
     * 处理登录事件
     *
     * @param request
     * @param loginUser
     * @param referer
     * @return
     */
    private ModelAndView processLogin(final HttpServletRequest request, final User loginUser, String referer) {
        ModelAndView view = new ModelAndView();
        if (loginUser != null) {
            // 设置登录用户的状态
            loginUser.setLogin(true);
            // 更新redis session信息，用以支持sso
            agentSessionProxy.updateUserSession(
                    loginUser.getId(), MainUtils.getContextID(request.getSession().getId()), loginUser.getOrgi());
            loginUser.setSessionid(MainUtils.getContextID(request.getSession().getId()));


            if (StringUtils.isNotBlank(referer)) {
                view = new ModelAndView("redirect:" + referer);
            } else {
                view = new ModelAndView("redirect:/");
            }

            // 登录成功 判断是否进入多租户页面
            SystemConfig systemConfig = MainUtils.getSystemConfig();
            if (systemConfig != null && systemConfig.isEnabletneant() && systemConfig.isTenantconsole() && !loginUser.isSuperuser()) {
                view = new ModelAndView("redirect:/apps/tenant/index");
            }
            List<UserRole> userRoleList = userRoleRes.findByOrgiAndUser(loginUser.getOrgi(), loginUser);
            if (userRoleList != null && userRoleList.size() > 0) {
                for (UserRole userRole : userRoleList) {
                    loginUser.getRoleList().add(userRole.getRole());
                }
            }

            // 获取用户部门以及下级部门
            UserProxy.attachOrgansPropertiesForUser(loginUser);

            // 获取用户的角色权限，进行授权
            List<RoleAuth> roleAuthList = roleAuthRes.findAll(new Specification<RoleAuth>() {
                @Override
                public Predicate toPredicate(
                        Root<RoleAuth> root, CriteriaQuery<?> query,
                        CriteriaBuilder cb) {
                    List<Predicate> list = new ArrayList<Predicate>();
                    if (loginUser.getRoleList() != null && loginUser.getRoleList().size() > 0) {
                        for (Role role : loginUser.getRoleList()) {
                            list.add(cb.equal(root.get("roleid").as(String.class), role.getId()));
                        }
                    }
                    Predicate[] p = new Predicate[list.size()];
                    cb.and(cb.equal(root.get("orgi").as(String.class), loginUser.getOrgi()));
                    return cb.or(list.toArray(p));
                }
            });

            if (roleAuthList != null) {
                for (RoleAuth roleAuth : roleAuthList) {
                    loginUser.getRoleAuthMap().put(roleAuth.getDicvalue(), true);
                }
            }

            loginUser.setLastlogintime(new Date());
            if (StringUtils.isNotBlank(loginUser.getId())) {
                userRepository.save(loginUser);
            }

            super.setUser(request, loginUser);
            // 当前用户 企业id为空 调到创建企业页面
            if (StringUtils.isBlank(loginUser.getOrgid())) {
                view = new ModelAndView("redirect:/apps/organization/add.html");
            }
        }
        return view;
    }


    /**
     * 登出用户
     * code代表登出的原因
     *
     * @param request
     * @param response
     * @param code     登出的代码
     * @return
     */
    @RequestMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, @RequestParam(value = "code", required = false) String code) throws UnsupportedEncodingException {
        final User user = super.getUser(request);
        request.getSession().removeAttribute(Constants.USER_SESSION_NAME);
        request.getSession().invalidate();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie != null && StringUtils.isNotBlank(cookie.getName()) && StringUtils.isNotBlank(
                        cookie.getValue())) {
                    if (cookie.getName().equals(Constants.CSKEFU_SYSTEM_COOKIES_FLAG)) {
                        cookie.setMaxAge(0);
                        response.addCookie(cookie);
                    }
                }
            }
        }

        if (StringUtils.isNotBlank(code)) {
            return "redirect:/?msg=" + code;
        }

        return "redirect:/";
    }

    @RequestMapping(value = "/register")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView register(HttpServletRequest request, HttpServletResponse response, @Valid String msg) {
        ModelAndView view = request(super.createRequestPageTempletResponse("redirect:/"));
        if (request.getSession(true).getAttribute(Constants.USER_SESSION_NAME) == null) {
            view = request(super.createRequestPageTempletResponse("/register"));
        }
        if (StringUtils.isNotBlank(msg)) {
            view.addObject("msg", msg);
        }
        return view;
    }

    @RequestMapping("/addAdmin")
    @Menu(type = "apps", subtype = "user", access = true)
    public ModelAndView addAdmin(HttpServletRequest request, HttpServletResponse response, @Valid User user) {
        String msg = "";
        msg = validUser(user);
        if (StringUtils.isNotBlank(msg)) {
            return request(super.createRequestPageTempletResponse("redirect:/register.html?msg=" + msg));
        } else {
            user.setUname(user.getUsername());
            user.setUsertype("0");
            if (StringUtils.isNotBlank(user.getPassword())) {
                user.setPassword(MainUtils.md5(user.getPassword()));
            }
            user.setOrgi(super.getOrgiByTenantshare(request));
    		/*if(StringUtils.isNotBlank(super.getUser(request).getOrgid())) {
    			user.setOrgid(super.getUser(request).getOrgid());
    		}else {
    			user.setOrgid(MainContext.SYSTEM_ORGI);
    		}*/
            userRepository.save(user);
            OnlineUserProxy.clean(super.getOrgi(request));

        }
        ModelAndView view = this.processLogin(request, user, "");
        //当前用户 企业id为空 调到创建企业页面
        if (StringUtils.isBlank(user.getOrgid())) {
            view = request(super.createRequestPageTempletResponse("redirect:/apps/organization/add.html"));
        }
        return view;
    }

    private String validUser(User user) {
        String msg = "";
        User tempUser = userRepository.findByUsernameAndDatastatus(user.getUsername(), false);
        if (tempUser != null) {
            msg = "username_exist";
            return msg;
        }
        tempUser = userRepository.findByEmailAndDatastatus(user.getEmail(), false);
        if (tempUser != null) {
            msg = "email_exist";
            return msg;
        }
        tempUser = userRepository.findByMobileAndDatastatus(user.getMobile(), false);
        if (tempUser != null) {
            msg = "mobile_exist";
            return msg;
        }
        return msg;
    }
}