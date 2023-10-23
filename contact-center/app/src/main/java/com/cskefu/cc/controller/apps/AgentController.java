/*
 * Copyright (C) 2023 Beijing Huaxia Chunsong Technology Co., Ltd.
 * <https://www.chatopera.com>, Licensed under the Chunsong Public
 * License, Version 1.0  (the "License"), https://docs.cskefu.com/licenses/v1.html
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Copyright (C) 2018-Jun. 2023 Chatopera Inc, <https://www.chatopera.com>,  Licensed under the Apache License, Version 2.0,
 * http://www.apache.org/licenses/LICENSE-2.0
 * Copyright (C) 2017 优客服-多渠道客服系统,  Licensed under the Apache License, Version 2.0,
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.cskefu.cc.controller.apps;

import com.alibaba.fastjson.JSONObject;
import com.cskefu.cc.acd.ACDAgentService;
import com.cskefu.cc.acd.ACDWorkMonitor;
import com.cskefu.cc.activemq.BrokerPublisher;
import com.cskefu.cc.basic.Constants;
import com.cskefu.cc.basic.MainContext;
import com.cskefu.cc.basic.MainUtils;
import com.cskefu.cc.cache.Cache;
import com.cskefu.cc.controller.Handler;
import com.cskefu.cc.util.restapi.RestUtils;
import com.cskefu.cc.exception.CSKefuException;
import com.cskefu.cc.model.*;
import com.cskefu.cc.peer.PeerSyncIM;
import com.cskefu.cc.persistence.blob.JpaBlobHelper;
import com.cskefu.cc.persistence.interfaces.DataExchangeInterface;
import com.cskefu.cc.persistence.repository.*;
import com.cskefu.cc.proxy.*;
import com.cskefu.cc.socketio.message.ChatMessage;
import com.cskefu.cc.socketio.message.Message;
import com.cskefu.cc.util.Menu;
import com.cskefu.cc.util.PinYinTools;
import com.cskefu.cc.util.PropertiesEventUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/agent")
public class AgentController extends Handler {

    static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    @Autowired
    private ACDWorkMonitor acdWorkMonitor;

    @Autowired
    private ACDAgentService acdAgentService;

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private PropertiesEventRepository propertiesEventRes;

    @Autowired
    private AgentUserRepository agentUserRes;

    @Autowired
    private AgentStatusRepository agentStatusRes;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private PassportWebIMUserRepository onlineUserRes;

    @Autowired
    private WeiXinUserRepository weiXinUserRes;

    @Autowired
    private ServiceSummaryRepository serviceSummaryRes;

    @Autowired
    private ChatMessageRepository chatMessageRes;

    @Autowired
    private AgentProxy agentProxy;

    @Autowired
    private TagRepository tagRes;

    @Autowired
    private TagRelationRepository tagRelationRes;

    @Autowired
    private AgentUserTaskRepository agentUserTaskRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private StatusEventRepository statusEventRes;

    @Autowired
    private AgentUserProxy agentUserProxy;

    @Autowired
    private PbxHostRepository pbxHostRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Autowired
    private StreamingFileRepository streamingFileRes;

    @Autowired
    private JpaBlobHelper jpaBlobHelper;

    @Autowired
    private BlackEntityProxy blackEntityProxy;

    @Autowired
    private Cache cache;

    @Autowired
    private AgentServiceProxy agentServiceProxy;

    @Value("${web.upload-path}")
    private String webUploadPath;

    @Autowired
    @Lazy
    private PeerSyncIM peerSyncIM;

    @Autowired
    private BrokerPublisher brokerPublisher;

    @Autowired
    private AgentStatusProxy agentStatusProxy;

    @Autowired
    private UserProxy userProxy;

    @Autowired
    private OrganProxy organProxy;

    @Autowired
    private OrganRepository organRes;

    @Autowired
    private ChatbotRepository chatbotRes;

    /**
     * 坐席从联系人列表进入坐席工作台和该联系人聊天
     *
     * @param map
     * @param request
     * @param response
     * @param sort
     * @param channels  可立即触达的渠道
     * @param contactid
     * @return
     * @throws IOException
     * @throws TemplateException
     */
    @RequestMapping("/proactive")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView proactive(
            ModelMap map,
            HttpServletRequest request,
            HttpServletResponse response,
            @Valid String sort,
            @Valid String channels,
            @RequestParam(name = "contactid", required = false) String contactid) throws IOException, CSKefuException {

        if (StringUtils.isBlank(contactid)) {
            logger.info("[chat] empty contactid, fast return error page.");
            return request(super.createView("/public/error"));
        }

        logger.info(
                "[chat] contactid {}, channels {}", contactid,
                channels);

        final User logined = super.getUser(request);
        AgentUser agentUser = agentUserProxy.figureAgentUserBeforeChatWithContactInfo(channels, contactid, logined);

        if (agentUser != null) {
            logger.info(
                    "[chat] resolved agentUser, figure view model data as index page, agentUserId {}, onlineUser Id {}, agentno {}, channel {}",
                    agentUser.getId(), agentUser.getUserid(), agentUser.getAgentno(), agentUser.getChanneltype());
        } else {
            logger.info("[chat] can not resolve agentUser !!!");
        }

        // TODO 在agentUser没有得到的情况下，传回的前端信息增加提示，提示放在modelview中

        // 处理原聊天数据
        ModelAndView view = request(super.createView("/apps/agent/index"));
        agentUserProxy.buildIndexViewWithModels(view, map, request, response, sort, logined, agentUser);
        return view;
    }


    /**
     * 打开坐席工作台
     *
     * @param map
     * @param request
     * @param response
     * @param sort
     * @return
     * @throws IOException
     * @throws TemplateException
     */
    @RequestMapping("/index")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView index(
            ModelMap map,
            HttpServletRequest request,
            HttpServletResponse response,
            @Valid String sort,
            boolean licenseVerifiedPass,
            String licenseBillingMsg) throws IOException {
        logger.info("[index] licenseVerifiedPass {}, licenseBillingMsg {}", licenseVerifiedPass, licenseBillingMsg);
        final User logined = super.getUser(request);
        ModelAndView view = request(super.createView("/apps/agent/index"));
        agentUserProxy.buildIndexViewWithModels(view, map, request, response, sort, logined, null);
        return view;
    }

    @RequestMapping("/agentusers")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView agentusers(HttpServletRequest request,
                                   String userid,
                                   boolean licenseVerifiedPass,
                                   String licenseBillingMsg) {
        logger.info("[agentusers] userid {}, licenseVerifiedPass {}, licenseBillingMsg {}", userid, licenseVerifiedPass, licenseBillingMsg);
        ModelAndView view = request(super.createView("/apps/agent/agentusers"));
        User logined = super.getUser(request);
        view.addObject(
                "agentUserList", agentUserRes.findByAgentno(logined.getId(),
                        Sort.by(Direction.DESC, "status")));
        List<AgentUser> agentUserList = agentUserRes.findByUserid(userid);
        view.addObject(
                "curagentuser", agentUserList != null && agentUserList.size() > 0 ? agentUserList.get(0) : null);
        view.addObject("licenseVerifiedPass", licenseVerifiedPass);
        view.addObject("licenseBillingMsg", licenseBillingMsg);
        return view;
    }

    @RequestMapping("/agentuserpage")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView agentuserpage(
            ModelMap map,
            HttpServletRequest request,
            String id,
            Integer page,
            Integer current) throws IOException {
        String mainagentuserconter = "/apps/agent/mainagentuserconter";
        ModelAndView view = request(super.createView(mainagentuserconter));
        AgentUser agentUser = agentUserRes.findById(id).orElse(null);
        if (agentUser != null) {
            view.addObject("curagentuser", agentUser);
            view.addObject(
                    "agentUserMessageList", this.chatMessageRes.findByusession(agentUser.getUserid(), current));
        }
        return view;
    }

    @RequestMapping("/agentuserLabel")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView agentuserLabel(
            ModelMap map,
            HttpServletRequest request,
            String iconid) throws IOException {
        String mainagentuserconter = "/apps/agent/mainagentuserconter";
        ModelAndView view = request(super.createView(mainagentuserconter));
        ChatMessage labelid = this.chatMessageRes.findById(iconid).orElse(null);
        if (labelid != null) {
            if (labelid.isIslabel() == false) {
                labelid.setIslabel(true);
            } else {
                labelid.setIslabel(false);
            }
            chatMessageRes.save(labelid);
        }
        return view;
    }

    @RequestMapping("/agentusersearch")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView agentusersearch(
            ModelMap map,
            HttpServletRequest request,
            String id,
            String search,
            String condition
    ) throws IOException {
        String mainagentuserconter = "/apps/agent/mainagentusersearch";
        ModelAndView view = request(super.createView(mainagentuserconter));
        AgentUser agentUser = agentUserRes.findById(id).orElse(null);

        if (agentUser != null) {
            Page<ChatMessage> agentUserMessageList = null;
            if (condition.equals("label")) {
                agentUserMessageList = this.chatMessageRes.findByislabel(
                        agentUser.getUserid(), search, PageRequest.of(0, 9999, Direction.DESC, "updatetime"));
            } else {
                agentUserMessageList = this.chatMessageRes.findByUsessionAndMessageContaining(
                        agentUser.getUserid(), search, PageRequest.of(0, 9999, Direction.DESC, "updatetime"));
            }
            view.addObject("agentUserMessageList", agentUserMessageList);
        }
        return view;
    }


    @RequestMapping("/agentusersearchdetails")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView agentusersearchdetails(
            ModelMap map,
            HttpServletRequest request,
            String id,
            String createtime,
            String thisid) throws IOException, ParseException {
        String mainagentuserconter = "/apps/agent/mainagentuserconter";
        ModelAndView view = request(super.createView(mainagentuserconter));
        AgentUser agentUser = agentUserRes.findById(id).orElse(null);
        if (agentUser != null) {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = formatter.parse(createtime);
            view.addObject("agentusersearchdetails", thisid);
            view.addObject(
                    "agentUserMessageList", this.chatMessageRes.findByCreatetime(agentUser.getUserid(), date));
            view.addObject(
                    "agentUserMessageListnum", this.chatMessageRes.countByUsessionAndCreatetimeGreaterThanEqual(
                            agentUser.getUserid(), date));
        }
        return (view);
    }

    @RequestMapping("/agentuser")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView agentuser(
            ModelMap map,
            HttpServletRequest request,
            String id,
            String channel) throws IOException {
        // set default Value as WEBIM
        String mainagentuser = "/apps/agent/mainagentuser";
        switch (MainContext.ChannelType.toValue(channel)) {
            case MESSENGER:
                mainagentuser = "/apps/agent/mainagentuser_messenger";
                break;
            case PHONE:
                mainagentuser = "/apps/agent/mainagentuser_callout";
                break;
            case SKYPE:
                mainagentuser = "/apps/agent/mainagentuser_skype";
                break;
        }

        ModelAndView view = request(super.createView(mainagentuser));
        final User logined = super.getUser(request);
        AgentUser agentUser = agentUserRes.findById(id).orElse(null);

        if (agentUser != null) {
            view.addObject("curagentuser", agentUser);

            Chatbot c = chatbotRes.findBySnsAccountIdentifier(agentUser.getAppid());
            if (c != null) {
                view.addObject("aisuggest", c.isAisuggest());
            }

            view.addObject("inviteData", OnlineUserProxy.consult(agentUser.getAppid()));
            AgentUserTask agentUserTask = agentUserTaskRes.findById(id).orElse(null);
            agentUserTask.setTokenum(0);
            agentUserTaskRes.save(agentUserTask);

            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                List<AgentServiceSummary> summarizes = this.serviceSummaryRes.findByAgentserviceid(
                        agentUser.getAgentserviceid());
                if (summarizes.size() > 0) {
                    view.addObject("summary", summarizes.get(0));
                }
            }

            view.addObject(
                    "agentUserMessageList",
                    this.chatMessageRes.findByUsession(agentUser.getUserid(),
                            PageRequest.of(0, 20, Direction.DESC,
                                    "updatetime")));
            AgentService agentService = null;
            if (StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                agentService = this.agentServiceRes.findById(agentUser.getAgentserviceid()).orElse(null);
                view.addObject("curAgentService", agentService);
                if (agentService != null) {
                    /**
                     * 获取关联数据
                     */
                    agentServiceProxy.processRelaData(logined.getId(), agentService, map);
                }
            }
            if (MainContext.ChannelType.WEIXIN.toString().equals(agentUser.getChanneltype())) {
                List<PassportWechatUser> passportWechatUserList = weiXinUserRes.findByOpenid(agentUser.getUserid());
                if (passportWechatUserList.size() > 0) {
                    PassportWechatUser passportWechatUser = passportWechatUserList.get(0);
                    view.addObject("weiXinUser", passportWechatUser);
                }
            } else if (MainContext.ChannelType.WEBIM.toString().equals(agentUser.getChanneltype())) {
                PassportWebIMUser passportWebIMUser = onlineUserRes.findById(agentUser.getUserid()).orElse(null);
                if (passportWebIMUser != null) {
                    if (passportWebIMUser.getLogintime() != null) {
                        if (MainContext.OnlineUserStatusEnum.OFFLINE.toString().equals(passportWebIMUser.getStatus())) {
                            passportWebIMUser.setBetweentime(
                                    (int) (passportWebIMUser.getUpdatetime().getTime() - passportWebIMUser.getLogintime().getTime()));
                        } else {
                            passportWebIMUser.setBetweentime(
                                    (int) (System.currentTimeMillis() - passportWebIMUser.getLogintime().getTime()));
                        }
                    }
                    view.addObject("onlineUser", passportWebIMUser);
                }
            } else if (MainContext.ChannelType.PHONE.toString().equals(agentUser.getChanneltype())) {
                if (agentService != null && StringUtils.isNotBlank(agentService.getOwner())) {
                    StatusEvent statusEvent = this.statusEventRes.findById(agentService.getOwner()).orElse(null);
                    if (statusEvent != null) {
                        if (StringUtils.isNotBlank(statusEvent.getHostid())) {
                            view.addObject("pbxHost", pbxHostRes.findById(statusEvent.getHostid()).orElse(null));
                        }
                        view.addObject("statusEvent", statusEvent);
                    }
                }
            }

            view.addObject("serviceCount", this.agentServiceRes
                    .countByUseridAndStatus(agentUser
                                    .getUserid(),
                            MainContext.AgentUserStatusEnum.END
                                    .toString()));
            view.addObject("tagRelationList", tagRelationRes.findByUserid(agentUser.getUserid()));
        }

        AgentService service = agentServiceRes.findById(agentUser.getAgentserviceid()).orElse(null);
        if (service != null) {
            view.addObject("tags", tagRes.findByTagtypeAndSkill(MainContext.ModelType.USER.toString(), service.getSkill()));
        }
        return view;
    }

    @RequestMapping("/workorders/list")
    @Menu(type = "apps", subtype = "workorderslist")
    public ModelAndView workorderslist(HttpServletRequest request, String contactsid, ModelMap map) {
        if (MainContext.hasModule(Constants.CSKEFU_MODULE_WORKORDERS) && StringUtils.isNotBlank(contactsid)) {
            DataExchangeInterface dataExchange = (DataExchangeInterface) MainContext.getContext().getBean(
                    "workorders");
            if (dataExchange != null) {
                map.addAttribute(
                        "workOrdersList",
                        dataExchange.getListDataById(contactsid, super.getUser(request).getId()));
            }
            map.addAttribute("contactsid", contactsid);
        }
        return request(super.createView("/apps/agent/workorders"));
    }

    /**
     * 设置为就绪，置闲
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/ready")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView ready(HttpServletRequest request) {
        final User logined = super.getUser(request);
        final AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentno(
                logined.getId(), logined.getSkills());

        // 缓存就绪状态
        agentProxy.ready(logined, agentStatus, false);

        // 为该坐席分配访客
        acdAgentService.assignVisitors(agentStatus.getAgentno());
        acdWorkMonitor.recordAgentStatus(agentStatus.getAgentno(),
                agentStatus.getUsername(),
                agentStatus.getAgentno(),
                logined.isAdmin(), // 0代表admin
                agentStatus.getAgentno(),
                MainContext.AgentStatusEnum.NOTREADY.toString(),
                MainContext.AgentStatusEnum.READY.toString(),
                MainContext.AgentWorkType.MEIDIACHAT.toString(),
                null);

        return request(super.createView("/public/success"));
    }

    /**
     * 将一个已经就绪的坐席设置为未就绪的状态
     * 这个接口并不会重新分配坐席的现在的服务中的访客给其它坐席
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/notready")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView notready(HttpServletRequest request) {
        final User logined = super.getUser(request);
        logger.info("[notready] set user {} as not ready", logined.getId());
        AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentno(
                logined.getId(), logined.getSkills());

        agentStatus.setBusy(false);
        agentStatus.setUpdatetime(new Date());
        agentStatus.setStatus(MainContext.AgentStatusEnum.NOTREADY.toString());
        cache.putAgentStatus(agentStatus);
        agentStatusRes.save(agentStatus);

        agentStatusProxy.broadcastAgentsStatus("agent", "notready", agentStatus.getAgentno());

        acdWorkMonitor.recordAgentStatus(agentStatus.getAgentno(),
                agentStatus.getUsername(),
                agentStatus.getAgentno(),
                logined.isAdmin(), // 0代表admin
                agentStatus.getAgentno(),
                MainContext.AgentStatusEnum.READY.toString(),
                MainContext.AgentStatusEnum.NOTREADY.toString(),
                MainContext.AgentWorkType.MEIDIACHAT.toString(),
                null);

        return request(super.createView("/public/success"));
    }

    /**
     * 设置状态：就绪，置忙
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/busy")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView busy(HttpServletRequest request) {
        final User logined = super.getUser(request);
        logger.info("[busy] set user {} as busy", logined.getId());
        AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentno(
                logined.getId(), logined.getSkills());

        agentStatus.setBusy(true);
        acdWorkMonitor.recordAgentStatus(
                agentStatus.getAgentno(),
                agentStatus.getUsername(),
                agentStatus.getAgentno(),
                super.getUser(request).isAdmin(),
                agentStatus.getAgentno(),
                MainContext.AgentStatusEnum.NOTBUSY.toString(),
                MainContext.AgentStatusEnum.BUSY.toString(),
                MainContext.AgentWorkType.MEIDIACHAT.toString(),
                agentStatus.getUpdatetime());
        agentStatus.setUpdatetime(new Date());
        cache.putAgentStatus(agentStatus);
        agentStatusRes.save(agentStatus);

        agentStatusProxy.broadcastAgentsStatus("agent", "busy", logined.getId());

        return request(super.createView("/public/success"));
    }

    /**
     * 设置状态：就绪，置闲
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/notbusy")
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView notbusy(HttpServletRequest request) {
        final User logined = super.getUser(request);
        // 组织结构和权限数据
        logger.info("[notbusy] set user {} as not busy", logined.getId());

        AgentStatus agentStatus = agentProxy.resolveAgentStatusByAgentno(
                logined.getId(), logined.getSkills());

        // 设置为就绪，置闲
        agentStatus.setBusy(false);
        agentStatus.setUpdatetime(new Date());
        agentStatus.setStatus(MainContext.AgentStatusEnum.READY.toString());

        // 更新工作记录
        acdWorkMonitor.recordAgentStatus(
                agentStatus.getAgentno(),
                agentStatus.getUsername(),
                agentStatus.getAgentno(),
                super.getUser(request).isAdmin(),
                agentStatus.getAgentno(),
                MainContext.AgentStatusEnum.BUSY.toString(),
                MainContext.AgentStatusEnum.NOTBUSY.toString(),
                MainContext.AgentWorkType.MEIDIACHAT.toString(),
                agentStatus.getUpdatetime());

        // 更新数据库和缓存
        cache.putAgentStatus(agentStatus);
        agentStatusRes.save(agentStatus);

        // 重新分配访客给坐席
        acdAgentService.assignVisitors(agentStatus.getAgentno());

        return request(super.createView("/public/success"));
    }

    @RequestMapping(value = "/clean")
    @Menu(type = "apps", subtype = "clean")
    public ModelAndView clean(HttpServletRequest request) throws Exception {
        List<AgentUser> agentUserList = agentUserRes.findByAgentnoAndStatus(
                super.getUser(request).getId(), MainContext.AgentUserStatusEnum.END.toString());
        List<AgentService> agentServiceList = new ArrayList<>();
        for (AgentUser agentUser : agentUserList) {
            if (agentUser != null && super.getUser(request).getId().equals(agentUser.getAgentno())) {
                acdAgentService.finishAgentUser(agentUser);
                AgentService agentService = agentServiceRes.findById(agentUser.getAgentserviceid()).orElse(null);
                if (agentService != null) {
                    agentService.setStatus(MainContext.AgentUserStatusEnum.END.toString());
                    agentServiceList.add(agentService);
                }
            }
        }
        agentServiceRes.saveAll(agentServiceList);
        return request(super
                .createView("redirect:/agent/index.html"));
    }


    /**
     * 结束对话
     * 如果当前对话属于登录用户或登录用户为超级用户，则可以结束这个对话
     *
     * @param request
     * @param id
     * @return
     * @throws Exception
     */
    @RequestMapping({"/end"})
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView end(HttpServletRequest request, @Valid String id) {
        logger.info("[end] end id {}", id);
        final User logined = super.getUser(request);

        final AgentUser agentUser = agentUserRes.findById(id).orElse(null);

        if (agentUser != null) {
            if ((StringUtils.equals(
                    logined.getId(), agentUser.getAgentno()) || logined.isAdmin())) {
                // 删除访客-坐席关联关系，包括缓存
                try {
                    acdAgentService.finishAgentUser(agentUser);
                } catch (CSKefuException e) {
                    // 未能删除成功
                    logger.error("[end]", e);
                }
            } else {
                logger.info("[end] Permission not fulfill.");
            }
        }

        return request(super
                .createView("redirect:/agent/index.html"));
    }

    @RequestMapping({"/readmsg"})
    @Menu(type = "apps", subtype = "agent")
    public ModelAndView readmsg(HttpServletRequest request, @Valid String userid) {

        AgentUserTask agentUserTask = agentUserTaskRes.findById(userid).orElse(null);
        agentUserTask.setTokenum(0);
        agentUserTaskRes.save(agentUserTask);
        return request(super.createView("/public/success"));
    }

    @RequestMapping({"/blacklist/add"})
    @Menu(type = "apps", subtype = "blacklist")
    public ModelAndView blacklistadd(ModelMap map, HttpServletRequest request, @Valid String agentuserid, @Valid String agentserviceid, @Valid String userid)
            throws Exception {
        map.addAttribute("agentuserid", agentuserid);
        map.addAttribute("agentserviceid", agentserviceid);
        map.addAttribute("userid", userid);
        map.addAttribute("agentUser", agentUserRes.findById(userid).orElse(null));
        return request(super.createView("/apps/agent/blacklistadd"));
    }

    @RequestMapping({"/blacklist/save"})
    @Menu(type = "apps", subtype = "blacklist")
    public ModelAndView blacklist(
            HttpServletRequest request,
            @Valid String agentuserid,
            @Valid String agentserviceid,
            @Valid String userid,
            @Valid BlackEntity blackEntity)
            throws Exception {
        logger.info("[blacklist] userid {}", userid);
        final User logined = super.getUser(request);

        if (StringUtils.isBlank(userid)) {
            throw new CSKefuException("Invalid userid");
        }
        /**
         * 添加黑名单
         * 一定时间后触发函数
         */
        JSONObject payload = new JSONObject();

        int timeSeconds = blackEntity.getControltime() * 3600;
        payload.put("userId", userid);
        ModelAndView view = end(request, agentuserid);

        // 更新或创建黑名单
        blackEntityProxy.updateOrCreateBlackEntity(blackEntity, logined, userid, agentserviceid, agentuserid);

        // 创建定时任务 取消拉黑
        brokerPublisher.send(
                Constants.WEBIM_SOCKETIO_ONLINE_USER_BLACKLIST, payload.toJSONString(), false, timeSeconds);

        return view;
    }

    @RequestMapping("/tagrelation")
    @Menu(type = "apps", subtype = "tagrelation")
    public ModelAndView tagrelation(ModelMap map, HttpServletRequest request, @Valid String userid, @Valid String tagid, @Valid String dataid) {
        TagRelation tagRelation = tagRelationRes.findByUseridAndTagid(userid, tagid);
        if (tagRelation == null) {
            tagRelation = new TagRelation();
            tagRelation.setUserid(userid);
            tagRelation.setTagid(tagid);
            tagRelation.setDataid(dataid);
            tagRelationRes.save(tagRelation);
        } else {
            tagRelationRes.delete(tagRelation);
        }
        return request(super.createView("/public/success"));
    }

    /**
     * 坐席聊天时发送图片和文件
     *
     * @param map
     * @param request
     * @param multipart
     * @param id
     * @param paste     是否是粘贴到chatbox的图片事件，此时发送者还没有执行发送
     * @return
     * @throws IOException
     */
    @RequestMapping("/image/upload")
    @Menu(type = "im", subtype = "image")
    public ResponseEntity<String> upload(
            ModelMap map,
            HttpServletRequest request,
            @RequestParam(value = "imgFile", required = false) MultipartFile multipart,
            @Valid String id,
            @Valid boolean paste) throws IOException {
        logger.info("[upload] image file, agentUser id {}, paste {}", id, paste);
        final User logined = super.getUser(request);

        JSONObject result = new JSONObject();
        HttpHeaders headers = RestUtils.header();
        final AgentUser agentUser = agentUserRes.findById(id).orElse(null);

        if (multipart != null && multipart.getOriginalFilename().lastIndexOf(".") > 0) {
            try {
                StreamingFile sf = agentProxy.saveFileIntoMySQLBlob(logined, multipart);
                // 发送通知
                if (!paste) {
                    agentProxy.sendFileMessageByAgent(logined, agentUser, multipart, sf);
                }
                result.put("error", 0);
                result.put("url", sf.getFileUrl());
            } catch (CSKefuException e) {
                result.put("error", 1);
                result.put("message", "请选择文件");
            }
        } else {
            result.put("error", 1);
            result.put("message", "请选择图片文件");
        }
        return new ResponseEntity<>(result.toString(), headers, HttpStatus.OK);
    }

    @RequestMapping("/message/image")
    @Menu(type = "resouce", subtype = "image", access = true)
    public ModelAndView messageimage(HttpServletResponse response, ModelMap map, @Valid String id, @Valid String t) throws IOException {
        ChatMessage message = chatMessageRes.findById(id).orElse(null);
        map.addAttribute("chatMessage", message);
        map.addAttribute("agentUser", cache.findOneAgentUserByUserId(message.getUserid()));
    	/*if(StringUtils.isNotBlank(t)){
    		map.addAttribute("t", t) ;
    	}*/
        map.addAttribute("t", true);
        return request(super.createView("/apps/agent/media/messageimage"));
    }

    @RequestMapping("/message/image/upload")
    @Menu(type = "im", subtype = "image")
    public ModelAndView messageimage(
            ModelMap map,
            HttpServletRequest request,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @Valid String id,
            @Valid String userid,
            @Valid String fileid) throws IOException {
        logger.info("[messageimage] userid {}, chat message id {}, fileid {}", userid, id, fileid);
        if (image != null && StringUtils.isNotBlank(fileid)) {
            File tempFile = File.createTempFile(fileid, ".png");
            try {
                // 创建临时图片文件
                if (!tempFile.getParentFile().exists()) {
                    tempFile.getParentFile().mkdirs();
                }
                // 写入临时文件
                FileCopyUtils.copy(image.getBytes(), tempFile);
                ChatMessage chatMessage = chatMessageRes.findById(id).orElse(null);
                chatMessage.setCooperation(true);
                chatMessageRes.save(chatMessage);

                // 写入协作文件
                String fileName = "upload/" + fileid + "_cooperation";
                File imageFile = new File(webUploadPath, fileName);
                MainUtils.scaleImage(imageFile, tempFile, 0.1F);

                // 保存到数据库
                StreamingFile sf = streamingFileRes.findById(fileid).orElse(null);
                if (sf != null) {
                    sf.setCooperation(jpaBlobHelper.createBlobWithFile(imageFile));
                    streamingFileRes.save(sf);
                }

                cache.findOneAgentUserByUserId(
                        chatMessage.getUserid()).ifPresent(p -> {
                    Message outMessage = new Message();
                    outMessage.setMessage("/res/image.html?id=" + fileid + "&cooperation=true");
                    outMessage.setFilename(imageFile.getName());
                    outMessage.setAttachmentid(chatMessage.getAttachmentid());
                    outMessage.setFilesize((int) imageFile.length());
                    outMessage.setMessageType(MainContext.MediaType.ACTION.toString());
                    outMessage.setCalltype(MainContext.CallType.INVITE.toString());
                    outMessage.setCreatetime(Constants.DISPLAY_DATE_FORMATTER.format(new Date()));
                    outMessage.setAgentUser(p);

                    peerSyncIM.send(
                            MainContext.ReceiverType.VISITOR,
                            MainContext.ChannelType.toValue(p.getChanneltype()),
                            p.getAppid(),
                            MainContext.MessageType.MESSAGE,
                            p.getUserid(),
                            outMessage,
                            true);
                });
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }
        return request(super.createView("/public/success"));
    }


    /**
     * 坐席会话关联联系人
     *
     * @param map
     * @param request
     * @param contactsid     联系人ID
     * @param userid         访客ID
     * @param agentserviceid 坐席服务ID
     * @param agentuserid    坐席ID
     * @return
     */
    @RequestMapping(value = "/contacts")
    @Menu(type = "apps", subtype = "contacts")
    public ModelAndView contacts(
            ModelMap map,
            final HttpServletRequest request,
            @Valid String contactsid,
            @Valid String userid,
            @Valid String agentserviceid,
            @Valid String agentuserid) throws CSKefuException {
        logger.info(
                "[contacts] contactsid {}, userid {}, agentserviceid {}, agentuserid {}", contactsid, userid,
                agentserviceid, agentuserid);

        final User logined = super.getUser(request);

        if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(contactsid)) {

            /**
             * 获得联系人
             */
            Contacts contacts = contactsRes.findById(contactsid).orElse(null);
            if (contacts != null) {
                map.addAttribute("contacts", contacts);
            }

            /**
             * 在关联联系人后，更新AgentUser的显示的名字
             */
            AgentUser agentUser = agentUserRes.findById(agentuserid).orElse(null);
            if (agentUser != null) {
                agentUser.setUsername(contacts.getName());
                agentUser.setNickname(contacts.getName());
                agentUserRes.save(agentUser);
            }

            /**
             * 更新OnlineUser
             */
            PassportWebIMUser passportWebIMUser = onlineUserRes.findOneByUserid(userid);
            if (passportWebIMUser != null) {
                passportWebIMUser.setContactsid(contactsid);
                passportWebIMUser.setUsername(contacts.getName());
                passportWebIMUser.setUpdateuser(logined.getUname());
                onlineUserRes.save(passportWebIMUser);
            }

            AgentService agentService = agentServiceRes.findById(agentserviceid).orElse(null);
            if (agentService != null) {
                agentService.setContactsid(contactsid);
                agentService.setUsername(contacts.getName());
                agentServiceRes.save(agentService);

                AgentUserContacts agentUserContacts = agentUserContactsRes.findOneByUserid(
                        userid).orElseGet(() -> {
                    AgentUserContacts p = new AgentUserContacts();

                    p.setUserid(userid);
                    p.setCreater(super.getUser(request).getId());
                    p.setCreatetime(new Date());
                    return p;
                });

                agentUserContacts.setContactsid(contactsid);
                agentUserContacts.setAppid(agentService.getAppid());
                agentUserContacts.setChanneltype(agentService.getChanneltype());

                agentUserContactsRes.save(agentUserContacts);
            }
        }
        return request(super.createView("/apps/agent/contacts"));
    }


    @RequestMapping(value = "/clean/associated")
    @Menu(type = "apps", subtype = "cleanassociated")
    public ModelAndView cleanAssociated(ModelMap map, HttpServletRequest request, final @RequestParam String currentAgentUserContactsId) {
        String contactsid = null;
        if (StringUtils.isNotEmpty(currentAgentUserContactsId)) {
            AgentUserContacts agentUserContacts = agentUserContactsRes.findById(currentAgentUserContactsId).orElse(null);
            if (agentUserContacts != null) {
                agentUserContactsRes.delete(agentUserContacts);
            }
        }

        return request(super.createView("/apps/agent/contacts"));
    }

    @ResponseBody
    @RequestMapping(value = "/evaluation")
    @Menu(type = "apps", subtype = "evaluation")
    public String evaluation(HttpServletRequest request, @Valid String agentuserid) {
        AgentUser agentUser = agentUserRes.findById(agentuserid).orElse(null);

        Message outMessage = new Message();
        outMessage.setChannelMessage(agentUser);
        outMessage.setAgentUser(agentUser);

        peerSyncIM.send(
                MainContext.ReceiverType.VISITOR,
                MainContext.ChannelType.toValue(agentUser.getChanneltype()),
                agentUser.getAppid(),
                MainContext.MessageType.SATISFACTION,
                agentUser.getUserid(),
                outMessage,
                true);

        return "ok";
    }

    @RequestMapping(value = "/summary")
    @Menu(type = "apps", subtype = "summary")
    public ModelAndView summary(
            ModelMap map,
            HttpServletRequest request,
            @Valid String userid,
            @Valid String agentserviceid,
            @Valid String agentuserid,
            @Valid String channel) {
        if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
            AgentUser agentUser = this.agentUserRes.findById(agentuserid).orElse(null);
            if (agentUser != null && StringUtils.isNotBlank(agentUser.getAgentserviceid())) {
                List<AgentServiceSummary> summaries = this.serviceSummaryRes.findByAgentserviceid(
                        agentUser.getAgentserviceid());
                if (summaries.size() > 0) {
                    map.addAttribute("summary", summaries.get(0));
                }
            }
            Organ currentOrgan = super.getOrgan(request);
            if (null != currentOrgan) {
                map.addAttribute(
                        "tags", tagRes.findByTagtypeAndSkill(
                                MainContext.ModelType.CCSUMMARY.toString(), currentOrgan.getParent()));
            }


            map.addAttribute("userid", userid);
            map.addAttribute("agentserviceid", agentserviceid);
            map.addAttribute("agentuserid", agentuserid);
            map.addAttribute("channel", channel);
        }
        return request(super.createView("/apps/agent/summary"));
    }

    @RequestMapping(value = "/summary/save")
    @Menu(type = "apps", subtype = "summarysave")
    public ModelAndView summarysave(
            ModelMap map,
            HttpServletRequest request,
            @Valid AgentServiceSummary summary,
            @Valid String contactsid,
            @Valid String userid,
            @Valid String agentserviceid,
            @Valid String agentuserid,
            @Valid String channel) {
        if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
            summary.setCreater(super.getUser(request).getId());
            summary.setCreatetime(new Date());
            AgentService service = agentServiceRes.findById(agentserviceid).orElse(null);
            summary.setAgent(service.getAgentno());
            summary.setAgentno(service.getAgentno());
            summary.setSkill(service.getSkill());
            summary.setUsername(service.getUsername());
            summary.setAgentusername(service.getAgentusername());
            summary.setChannel(service.getChanneltype());
            summary.setContactsid(contactsid);
            summary.setLogindate(service.getLogindate());
            summary.setContactsid(service.getContactsid());
            summary.setEmail(service.getEmail());
            summary.setPhonenumber(service.getPhone());
            serviceSummaryRes.save(summary);
        }

        return request(super.createView(
                "redirect:/agent/agentuser.html?id=" + agentuserid + "&channel=" + channel));
    }

    /**
     * 坐席转接窗口
     *
     * @param map
     * @param request
     * @param userid
     * @param agentserviceid
     * @param agentuserid
     * @return
     */
    @RequestMapping(value = "/transfer")
    @Menu(type = "apps", subtype = "transfer")
    public ModelAndView transfer(
            ModelMap map,
            final HttpServletRequest request,
            final @Valid String userid,
            final @Valid String agentserviceid,
            final @Valid String agentuserid) {
        logger.info("[transfer] userId {}, agentUser {}", userid, agentuserid);
        final User logined = super.getUser(request);

        Organ targetOrgan = super.getOrgan(request);
        Map<String, Organ> ownOrgans = organProxy.findAllOrganByParent(targetOrgan);

        if (StringUtils.isNotBlank(userid) && StringUtils.isNotBlank(agentuserid)) {
            // 列出所有技能组
            List<Organ> skillGroups = organRes.findByIdInAndSkill(ownOrgans.keySet(), true);

            // 选择当前用户的默认技能组
            AgentService agentService = agentServiceRes.findById(agentserviceid).orElse(null);

            String currentOrgan = agentService.getSkill();

            if (StringUtils.isBlank(currentOrgan)) {
                if (!skillGroups.isEmpty()) {
                    currentOrgan = skillGroups.get(0).getId();
                }
            }
            logger.info("[transfer] set current organ as {}", currentOrgan);
            // 列出所有在线的坐席，排除本身
            List<String> userids = new ArrayList<>();
            final Map<String, AgentStatus> agentStatusMap = cache.findAllReadyAgentStatus();

            for (final String o : agentStatusMap.keySet()) {
                if (!StringUtils.equals(o, logined.getId())) {
                    userids.add(o);
                }
            }

            logger.info("[transfer] get all userids except mine, {}", StringUtils.join(userids, "\t"));

            final List<User> userList = userRes.findAllById(userids);
            for (final User o : userList) {
                o.setAgentStatus(agentStatusMap.get(o.getId()));
                // find user's skills
                userProxy.attachOrgansPropertiesForUser(o);
            }

            map.addAttribute("userList", userList);
            map.addAttribute("userid", userid);
            map.addAttribute("agentserviceid", agentserviceid);
            map.addAttribute("agentuserid", agentuserid);
            map.addAttribute("skillGroups", skillGroups);
            map.addAttribute("agentno", agentService.getAgentno());
            map.addAttribute("agentservice", this.agentServiceRes.findById(agentserviceid).orElse(null));
            map.addAttribute("currentorgan", currentOrgan);
        }

        return request(super.createView("/apps/agent/transfer"));
    }

    /**
     * 查找一个组织机构中的在线坐席
     *
     * @param map
     * @param request
     * @param organ
     * @return
     */
    @RequestMapping(value = "/transfer/agent")
    @Menu(type = "apps", subtype = "transferagent")
    public ModelAndView transferagent(
            ModelMap map,
            HttpServletRequest request,
            @Valid String organ,
            @Valid String agentid) {
        final User logined = super.getUser(request);
        if (StringUtils.isNotBlank(organ)) {
            List<String> userids = new ArrayList<>();

            final Map<String, AgentStatus> agentStatusMap = cache.findAllReadyAgentStatus();

            for (final String o : agentStatusMap.keySet()) {
                if (!StringUtils.equals(o, agentid)) {
                    userids.add(o);
                }
            }

            final List<User> userList = userRes.findAllById(userids);
            for (final User o : userList) {
                o.setAgentStatus(agentStatusMap.get(o.getId()));
                // find user's skills
                userProxy.attachOrgansPropertiesForUser(o);
            }
            map.addAttribute("userList", userList);
            map.addAttribute("currentorgan", organ);
        }
        return request(super.createView("/apps/agent/transferagentlist"));
    }


    @RequestMapping("/calloutcontact/add")
    @Menu(type = "apps", subtype = "calloutcontact", admin = true)
    public ModelAndView add(ModelMap map, HttpServletRequest request, @Valid String ckind) {
        map.addAttribute("ckind", ckind);
        return request(super.createView("/apps/agent/calloutcontact/add"));
    }

    @RequestMapping(value = "/calloutcontact/save")
    @Menu(type = "apps", subtype = "calloutcontact")
    public ModelAndView calloutcontactsave(
            ModelMap map,
            HttpServletRequest request,
            @RequestParam(value = "agentuser") String agentuser,
            @Valid Contacts contacts) throws CSKefuException {
        logger.info("[agent ctrl] calloutcontactsave agentuser [{}]", agentuser);
        AgentUser au = agentUserRes.findById(agentuser).orElse(null);
        if (au == null) {
            throw new CSKefuException("不存在该服务记录");
        }

        User logined = super.getUser(request);
        contacts.setId(MainUtils.getUUID());
        contacts.setCreater(logined.getId());
        contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
        if (StringUtils.isBlank(contacts.getCusbirthday())) {
            contacts.setCusbirthday(null);
        }
        contactsRes.save(contacts);

        AgentUserContacts auc = new AgentUserContacts();
        auc.setId(MainUtils.getUUID());
        auc.setUsername(au.getUsername());
        auc.setUserid(au.getUserid());
        auc.setContactsid(contacts.getId());
        auc.setChanneltype(au.getChanneltype());
        auc.setCreatetime(new Date());
        auc.setAppid(au.getAppid());
        auc.setCreater(logined.getId());
        agentUserContactsRes.save(auc);
        return request(super.createView("redirect:/agent/index.html"));
    }

    @RequestMapping("/calloutcontact/update")
    @Menu(type = "apps", subtype = "calloutcontact")
    public ModelAndView update(HttpServletRequest request, @Valid Contacts contacts) {
        Contacts data = contactsRes.findById(contacts.getId()).orElse(null);
        if (data != null) {
            List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(
                    request, contacts, data, "id", "creater", "createtime", "updatetime");    //记录 数据变更 历史
            if (events.size() > 0) {
                String modifyid = MainUtils.getUUID();
                Date modifytime = new Date();
                for (PropertiesEvent event : events) {
                    event.setDataid(contacts.getId());
                    event.setCreater(super.getUser(request).getId());
                    event.setModifyid(modifyid);
                    event.setCreatetime(modifytime);
                    propertiesEventRes.save(event);
                }
            }

            contacts.setCreater(data.getCreater());
            contacts.setCreatetime(data.getCreatetime());
            contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
            if (StringUtils.isBlank(contacts.getCusbirthday())) {
                contacts.setCusbirthday(null);
            }
            contactsRes.save(contacts);
        }

        return request(super.createView("redirect:/agent/index.html"));
    }
}
