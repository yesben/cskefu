/*
 * Copyright (C) 2023 Beijing Huaxia Chunsong Technology Co., Ltd.
 * <https://www.chatopera.com>, Licensed under the Chunsong Public
 * License, Version 1.0  (the "License"), https://docs.cskefu.com/licenses/v1.html
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Copyright (C) 2018- Jun. 2023 Chatopera Inc, <https://www.chatopera.com>,  Licensed under the Apache License, Version 2.0,
 * http://www.apache.org/licenses/LICENSE-2.0
 * Copyright (C) 2017 优客服-多渠道客服系统,  Licensed under the Apache License, Version 2.0,
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.cskefu.cc.controller.apps;

import com.cskefu.cc.basic.MainUtils;
import com.cskefu.cc.controller.Handler;
import com.cskefu.cc.exception.BillingQuotaException;
import com.cskefu.cc.exception.CSKefuException;
import com.cskefu.cc.model.*;
import com.cskefu.cc.persistence.repository.*;
import com.cskefu.cc.proxy.ContactsProxy;
import com.cskefu.cc.proxy.OrganProxy;
import com.cskefu.cc.util.Menu;
import com.cskefu.cc.util.PinYinTools;
import com.cskefu.cc.util.PropertiesEventUtil;
import com.cskefu.cc.util.dsdata.DSData;
import com.cskefu.cc.util.dsdata.DSDataEvent;
import com.cskefu.cc.util.dsdata.ExcelImportProecess;
import com.cskefu.cc.util.dsdata.export.ExcelExporterProcess;
import com.cskefu.cc.util.dsdata.process.ContactsProcess;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 联系人管理
 */
@Controller
@RequestMapping("/apps/contacts")
public class ContactsController extends Handler {

    private final static Logger logger = LoggerFactory.getLogger(ContactsController.class);

    @Autowired
    private ContactsRepository contactsRes;

    @Autowired
    private PropertiesEventRepository propertiesEventRes;

    @Autowired
    private ReporterRepository reporterRes;

    @Autowired
    private MetadataRepository metadataRes;

    @Autowired
    private ContactsProxy contactsProxy;

    @Autowired
    private OrganProxy organProxy;

    @Autowired
    private AgentServiceRepository agentServiceRes;

    @Autowired
    private AgentUserContactsRepository agentUserContactsRes;

    @Value("${web.upload-path}")
    private String path;

    @RequestMapping("/index")
    @Menu(type = "customer", subtype = "index")
    public ModelAndView index(
            ModelMap map,
            HttpServletRequest request,
            @Valid String q,
            @Valid String ckind
    ) throws CSKefuException {
        final User logined = super.getUser(request);
        final Organ currentOrgan = super.getOrgan(request);

        if (!super.preCheckPermissions(request)) {
            return request(super.createViewIncludedByFreemarkerTpl("/apps/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }

        map.addAttribute("currentOrgan", currentOrgan);

        Page<Contacts> contacts = contactsRes.findByOrganInAndSharesAllAndDatastatusFalse(
                super.getMyCurrentAffiliatesFlat(logined),
                PageRequest.of(super.getP(request), super.getPs(request)));

        map.addAttribute("contactsList", contacts);

        contactsProxy.bindContactsApproachableData(contacts, map, logined);

        return request(super.createView("/apps/contacts/index"));
    }

    @RequestMapping("/today")
    @Menu(type = "customer", subtype = "today")
    public ModelAndView today(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User logined = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParent(currentOrgan);

        if (!super.preCheckPermissions(request)) {
            return request(super.createView("/apps/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }

        Page<Contacts> contacts = contactsRes.findByCreaterAndSharesAndDatastatus(logined.getId(),
                logined.getId(),
                false,
                PageRequest.of(
                        super.getP(request),
                        super.getPs(request)));

        map.addAttribute(
                "contactsList", contacts);

        contactsProxy.bindContactsApproachableData(contacts, map, logined);

        return request(super.createView("/apps/contacts/index"));
    }

    @RequestMapping("/week")
    @Menu(type = "customer", subtype = "week")
    public ModelAndView week(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User logined = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParent(currentOrgan);

        if (!super.preCheckPermissions(request)) {
            return request(super.createView("/apps/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }

        Page<Contacts> contacts = contactsRes.findByCreaterAndSharesAndDatastatus(logined.getId(),
                logined.getId(),
                false,
                PageRequest.of(
                        super.getP(request),
                        super.getPs(request)));
        map.addAttribute(
                "contactsList", contacts);
        contactsProxy.bindContactsApproachableData(contacts, map, logined);


        return request(super.createView("/apps/contacts/index"));
    }

    @RequestMapping("/creater")
    @Menu(type = "customer", subtype = "creater")
    public ModelAndView creater(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind) throws CSKefuException {
        final User logined = super.getUser(request);

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParent(currentOrgan);

        if (!super.preCheckPermissions(request)) {
            return request(super.createView("/apps/contacts/index"));
        }

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }

        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }

        Page<Contacts> contacts = contactsRes.findByCreaterAndSharesAndDatastatus(logined.getId(),
                logined.getId(),
                false,
                PageRequest.of(
                        super.getP(request),
                        super.getPs(request)));


        map.addAttribute(
                "contactsList", contacts);
        contactsProxy.bindContactsApproachableData(contacts, map, logined);
        return request(super.createView("/apps/contacts/index"));
    }

    @RequestMapping("/delete")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView delete(HttpServletRequest request, @Valid Contacts contacts, @Valid String p, @Valid String ckind) {
        if (contacts != null) {
            contacts = contactsRes.findById(contacts.getId()).orElse(null);
            contacts.setDatastatus(true);                            //客户和联系人都是 逻辑删除
            contactsRes.save(contacts);
        }
        return request(super.createView(
                "redirect:/apps/contacts/index.html?p=" + p + "&ckind=" + ckind));
    }

    @RequestMapping("/add")
    @Menu(type = "contacts", subtype = "add")
    public ModelAndView add(ModelMap map, HttpServletRequest request, @Valid String ckind) {
        map.addAttribute("ckind", ckind);
        return request(super.createView("/apps/contacts/add"));
    }


    @RequestMapping("/save")
    @Menu(type = "contacts", subtype = "save")
    public ModelAndView save(
            ModelMap map,
            HttpServletRequest request,
            @Valid Contacts contacts,
            @RequestParam(name = "savefrom", required = false) String savefrom,
            @RequestParam(name = "idselflocation", required = false) String selflocation) {
        final User logined = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);
        String msg = "";

        // 添加数据
        try {
            contacts.setId(null);
            contacts.setCreater(logined.getId());

            if (currentOrgan != null && StringUtils.isBlank(contacts.getOrgan())) {
                contacts.setOrgan(currentOrgan.getId());
            }

            contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
            if (StringUtils.isBlank(contacts.getCusbirthday())) {
                contacts.setCusbirthday(null);
            }
            contactsRes.save(contacts);
            msg = "new_contacts_success";
        } catch (Exception e) {
            if (e instanceof UndeclaredThrowableException) {
                logger.error("[save] BillingQuotaException", e);
                if (StringUtils.startsWith(e.getCause().getMessage(), BillingQuotaException.SUFFIX)) {
                    msg = e.getCause().getMessage();
                }
            } else {
                logger.error("[save] err", e);
            }
        }
        return request(super.createView(
                "redirect:/apps/contacts/index.html?ckind=" + contacts.getCkind() + "&msg=" + msg));
    }

    @RequestMapping("/edit")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView edit(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String ckind) {
        map.addAttribute("contacts", contactsRes.findById(id).orElse(null));
        map.addAttribute("ckindId", ckind);
        return request(super.createView("/apps/contacts/edit"));
    }

    @RequestMapping("/detail")
    @Menu(type = "customer", subtype = "index")
    public ModelAndView detail(ModelMap map, HttpServletRequest request, @Valid String id) {
        if (id == null) {
            return null; // id is required. Block strange requst anyway with g2.min, https://github.com/alibaba/BizCharts/issues/143
        }
        map.addAttribute("contacts", contactsRes.findById(id).orElse(null));

        return request(super.createView("/apps/contacts/detail"));

    }

    // FIXME 待重构
//    @RequestMapping("mass")
//    @Menu(type = "contacts", subtype = "contacts")
//    public ModelAndView mass(
//            ModelMap map,
//            HttpServletRequest request,
//            @RequestParam(name = "ids", required = false) String ids,
//            @RequestParam(name = "massMessageToOnlineUserText", required = false) String massMessageToOnlineUserText,
//            @RequestParam(value = "massMessageToOnlineUserPic", required = false) MultipartFile multipart,
//            @RequestParam(value = "massMessageToOnlineUserFile", required = false) MultipartFile massMessageToOnlineUserFile,
//            @RequestParam(name = "paste", required = false) String paste,
//            @Valid String msg) throws IOException {
//        map.put("msg", msg);
//        final User logined = super.getUser(request);
//        boolean massStatus = false;
//
//        String massFileStyle = Constants.SKYPE_MESSAGE_TEXT;
//        if (StringUtils.isNotBlank(multipart.getOriginalFilename())) {
//            massFileStyle = Constants.SKYPE_MESSAGE_PIC;
//            contactsProxy.sendMessageToContacts(
//                    logined, ids, massMessageToOnlineUserText, multipart, massFileStyle, paste);
//        } else if (StringUtils.isNotBlank(massMessageToOnlineUserFile.getOriginalFilename())) {
//            massFileStyle = Constants.SKYPE_MESSAGE_FILE;
//            contactsProxy.sendMessageToContacts(
//                    logined, ids, massMessageToOnlineUserText, massMessageToOnlineUserFile, massFileStyle, paste);
//        } else {
//            contactsProxy.sendMessageToContacts(
//                    logined, ids, massMessageToOnlineUserText, massMessageToOnlineUserFile, massFileStyle, paste);
//        }
//
//        return request(
//                super.createRequestPageTempletResponse("redirect:/apps/contacts/index.html?massStatus=" + massStatus));
//    }


    @RequestMapping("/update")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView update(HttpServletRequest request, @Valid Contacts contacts, @Valid String ckindId) {
        final User logined = super.getUser(request);
        Contacts data = contactsRes.findById(contacts.getId()).orElse(null);
        String msg = "";

        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        Contacts theOnlyContact = contactsRes.findByskypeidAndDatastatus(
                skypeIDReplace, false);
        Contacts oldContact = contactsRes.findByIdAndDatastatus(contacts.getId(), false);

        Boolean determineChange = contactsProxy.determineChange(contacts, oldContact);
        /**
         * 验证skype唯一性验证
         */
        if (theOnlyContact == null || theOnlyContact.getId().equals(oldContact.getId())) {
            if (determineChange) {
                logger.info("[contacts edit] success :The contact has been modified successfully.");
                msg = "edit_contacts_success";
            } else {
                //无修改，直接点击确定
                return request(super.createView(
                        "redirect:/apps/contacts/index.html?ckind=" + ckindId));
            }
        } else {
            logger.info("[contacts edit] errer :The same skypeid exists");
            msg = "edit_contacts_fail";
            return request(super.createView(
                    "redirect:/apps/contacts/index.html?ckind=" + ckindId + "&msg=" + msg));
        }


        List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(
                request, contacts, data, "id", "creater", "createtime", "updatetime");    //记录 数据变更 历史
        if (events.size() > 0) {
            String modifyid = MainUtils.getUUID();
            Date modifytime = new Date();
            for (PropertiesEvent event : events) {
                event.setDataid(contacts.getId());
                event.setCreater(logined.getId());
                event.setModifyid(modifyid);
                event.setCreatetime(modifytime);
                propertiesEventRes.save(event);
            }
        }

        contacts.setSkypeid(contacts.getSkypeid());
        contacts.setCreater(data.getCreater());
        contacts.setOrgan(data.getOrgan());
        contacts.setCreatetime(data.getCreatetime());
        contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));

        if (StringUtils.isBlank(contacts.getCusbirthday())) {
            contacts.setCusbirthday(null);
        }

        if (msg.equals("edit_contacts_success")) {
            contactsRes.save(contacts);
        }
        return request(super.createView(
                "redirect:/apps/contacts/index.html?ckind=" + ckindId + "&msg=" + msg));
    }


    @RequestMapping("/imp")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView imp(ModelMap map, HttpServletRequest request, @Valid String ckind) {
        map.addAttribute("ckind", ckind);
        return request(super.createView("/apps/contacts/imp"));
    }

    @RequestMapping("/impsave")
    @Menu(type = "contacts", subtype = "contacts")
    public ModelAndView impsave(ModelMap map, HttpServletRequest request, @RequestParam(value = "cusfile", required = false) MultipartFile cusfile, @Valid String ckind) throws IOException {
        final User logined = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);
        String organId = currentOrgan != null ? currentOrgan.getId() : null;

        DSDataEvent event = new DSDataEvent();
        String fileName = "contacts/" + MainUtils.getUUID() + cusfile.getOriginalFilename().substring(
                cusfile.getOriginalFilename().lastIndexOf("."));
        File excelFile = new File(path, fileName);
        if (!excelFile.getParentFile().exists()) {
            excelFile.getParentFile().mkdirs();
        }
        MetadataTable table = metadataRes.findByTablename("uk_contacts");
        if (table != null) {
            FileUtils.writeByteArrayToFile(new File(path, fileName), cusfile.getBytes());
            event.setDSData(new DSData(table, excelFile, cusfile.getContentType(), logined));
            event.getDSData().setClazz(Contacts.class);
            event.getDSData().setProcess(new ContactsProcess(contactsRes));
            event.getValues().put("creater", logined.getId());
            event.getValues().put("organ", organId);
            event.getValues().put("shares", "all");
            reporterRes.save(event.getDSData().getReport());
            new ExcelImportProecess(event).process();        //启动导入任务
        }
        return request(super.createView("redirect:/apps/contacts/index.html"));
    }

    @RequestMapping("/expids")
    @Menu(type = "contacts", subtype = "contacts")
    public void expids(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String[] ids) throws IOException {
        if (ids != null && ids.length > 0) {
            Iterable<Contacts> contactsList = contactsRes.findAllById(Arrays.asList(ids));
            MetadataTable table = metadataRes.findByTablename("uk_contacts");
            List<Map<String, Object>> values = new ArrayList<>();
            for (Contacts contacts : contactsList) {
                values.add(MainUtils.transBean2Map(contacts));
            }

            response.setHeader(
                    "content-disposition",
                    "attachment;filename=CSKefu-Contacts-" + new SimpleDateFormat("yyyy-MM-dd").format(
                            new Date()) + ".xls");

            ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
            excelProcess.process();
        }

        return;
    }


    @RequestMapping("/expall")
    @Menu(type = "contacts", subtype = "contacts")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String ckind) throws IOException, CSKefuException {
        final User logined = super.getUser(request);
        if (!super.preCheckPermissions(request)) {
            return;
        }

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParent(currentOrgan);

        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }

        Iterable<Contacts> contactsList = contactsRes.findByCreaterAndSharesAndDatastatus(
                logined.getId(), logined.getId(), false, PageRequest.of(super.getP(request), super.getPs(request)));

        MetadataTable table = metadataRes.findByTablename("uk_contacts");
        List<Map<String, Object>> values = new ArrayList<>();
        for (Contacts contacts : contactsList) {
            values.add(MainUtils.transBean2Map(contacts));
        }

        response.setHeader(
                "content-disposition",
                "attachment;filename=CSKefu-Contacts-" + new SimpleDateFormat("yyyy-MM-dd").format(
                        new Date()) + ".xls");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();
        return;
    }

    @RequestMapping("/expsearch")
    @Menu(type = "contacts", subtype = "contacts")
    public void expall(ModelMap map, HttpServletRequest request, HttpServletResponse response, @Valid String q, @Valid String ckind) throws IOException {
        final User logined = super.getUser(request);

        Organ currentOrgan = super.getOrgan(request);
        Map<String, Organ> organs = organProxy.findAllOrganByParent(currentOrgan);

        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }

        Iterable<Contacts> contactsList = contactsRes.findByCreaterAndSharesAndDatastatus(
                logined.getId(), logined.getId(), false, PageRequest.of(super.getP(request), super.getPs(request)));
        MetadataTable table = metadataRes.findByTablename("uk_contacts");
        List<Map<String, Object>> values = new ArrayList<>();
        for (Contacts contacts : contactsList) {
            values.add(MainUtils.transBean2Map(contacts));
        }

        response.setHeader(
                "content-disposition",
                "attachment;filename=CSKefu-Contacts-" + new SimpleDateFormat("yyyy-MM-dd").format(
                        new Date()) + ".xls");

        ExcelExporterProcess excelProcess = new ExcelExporterProcess(values, table, response.getOutputStream());
        excelProcess.process();

        return;
    }


    @RequestMapping("/embed/index")
    @Menu(type = "customer", subtype = "embed")
    public ModelAndView embed(ModelMap map, HttpServletRequest request, @Valid String q, @Valid String ckind, @Valid String msg, @Valid String userid, @Valid String agentserviceid) throws CSKefuException {
        final User logined = super.getUser(request);
        map.put("msg", msg);
        if (!super.preCheckPermissions(request)) {
            return request(super.createViewIncludedByFreemarkerTpl("/apps/contacts/embed/index"));
        }
        if (StringUtils.isNotBlank(q)) {
            map.put("q", q);
        }
        if (StringUtils.isNotBlank(agentserviceid)) {
            map.put("agentserviceid", agentserviceid);
        }
        if (StringUtils.isNotBlank(ckind)) {
            map.put("ckind", ckind);
        }
        if (StringUtils.isNotBlank(agentserviceid)) {
            AgentService service = agentServiceRes.findById(agentserviceid).orElse(null);
        }
        Page<Contacts> contactsList = contactsRes.findByCreaterAndSharesAndDatastatus(
                logined.getId(), logined.getId(), false,
                PageRequest.of(super.getP(request), super.getPs(request)));

        map.addAttribute("contactsList", contactsList);

        if (StringUtils.isNotBlank(userid)) {
            agentUserContactsRes.findOneByUserid(userid).ifPresent(p -> {
                map.addAttribute("currentAgentUserContactsId", p.getId());
                map.addAttribute("currentContacsId", p.getContactsid());
            });
        }

        return request(super.createView("/apps/contacts/embed/index"));
    }

    @RequestMapping("/embed/add")
    @Menu(type = "contacts", subtype = "embedadd")
    public ModelAndView embedadd(ModelMap map, HttpServletRequest request, @Valid String agentserviceid) {
        if (StringUtils.isNotBlank(agentserviceid)) {
            map.put("agentserviceid", agentserviceid);
        }
        return request(super.createView("/apps/contacts/embed/add"));
    }

    @RequestMapping("/embed/save")
    @Menu(type = "contacts", subtype = "embedsave")
    public ModelAndView embedsave(HttpServletRequest request, @Valid Contacts contacts, @Valid String agentserviceid) {
        final User logined = super.getUser(request);
        Organ currentOrgan = super.getOrgan(request);
        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        String msg = "";
        Contacts contact = contactsRes.findByskypeidAndDatastatus(skypeIDReplace, false);

        //添加数据
        if (contacts.getSkypeid() != null && contact == null) {
            contacts.setCreater(logined.getId());

            if (StringUtils.isNotBlank(agentserviceid)) {
                AgentService agentService = agentServiceRes.findById(agentserviceid).orElse(null);
                contacts.setOrgan(agentService.getSkill());
            }

            contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));
            if (StringUtils.isBlank(contacts.getCusbirthday())) {
                contacts.setCusbirthday(null);
            }
            contactsRes.save(contacts);
            msg = "new_contacts_success";
            return request(
                    super.createView("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
        }
        msg = "new_contacts_fail";
        return request(super.createView("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
    }

    @RequestMapping("/embed/edit")
    @Menu(type = "contacts", subtype = "embededit")
    public ModelAndView embededit(ModelMap map, HttpServletRequest request, @Valid String id, @Valid String agentserviceid) {
        map.addAttribute("contacts", contactsRes.findById(id).orElse(null));
        if (StringUtils.isNotBlank(agentserviceid)) {
            map.addAttribute("agentserviceid", agentserviceid);
        }
        return request(super.createView("/apps/contacts/embed/edit"));
    }

    @RequestMapping("/embed/update")
    @Menu(type = "contacts", subtype = "embedupdate")
    public ModelAndView embedupdate(HttpServletRequest request, @Valid Contacts contacts, @Valid String agentserviceid) {
        final User logined = super.getUser(request);
        Contacts data = contactsRes.findById(contacts.getId()).orElse(null);
        String msg = "";
        String skypeIDReplace = contactsProxy.sanitizeSkypeId(contacts.getSkypeid());
        Contacts theOnlyContact = contactsRes.findByskypeidAndDatastatus(
                skypeIDReplace, false);
        Contacts oldContact = contactsRes.findByIdAndDatastatus(contacts.getId(), false);

        Boolean determineChange = contactsProxy.determineChange(contacts, oldContact);

        /**
         * 验证skype唯一性验证
         */
        if (theOnlyContact == null || theOnlyContact.getId().equals(oldContact.getId())) {
            if (determineChange) {
                logger.info("[contacts edit] success :The contact has been modified successfully.");
                msg = "edit_contacts_success";
            } else {
                //无修改，直接点击确定
                return request(super.createView("redirect:/apps/contacts/embed/index.html?agentserviceid=" + agentserviceid));
            }
        } else {
            logger.info("[contacts edit] errer :The same skypeid exists");
            msg = "edit_contacts_fail";
            return request(
                    super.createView("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
        }

        List<PropertiesEvent> events = PropertiesEventUtil.processPropertiesModify(
                request, contacts, data, "id", "creater", "createtime", "updatetime");    //记录 数据变更 历史
        if (events.size() > 0) {
            String modifyid = MainUtils.getUUID();
            Date modifytime = new Date();
            for (PropertiesEvent event : events) {
                event.setDataid(contacts.getId());
                event.setCreater(logined.getId());
                event.setModifyid(modifyid);
                event.setCreatetime(modifytime);
                propertiesEventRes.save(event);
            }
        }

        contacts.setSkypeid(contacts.getSkypeid());
        contacts.setCreater(data.getCreater());
        contacts.setOrgan(data.getOrgan());
        contacts.setCreatetime(data.getCreatetime());
        contacts.setPinyin(PinYinTools.getInstance().getFirstPinYin(contacts.getName()));


        if (StringUtils.isBlank(contacts.getCusbirthday())) {
            contacts.setCusbirthday(null);
        }

        if (msg.equals("edit_contacts_success")) {
            contactsRes.save(contacts);
        }
        return request(super.createView("redirect:/apps/contacts/embed/index.html?msg=" + msg + "&agentserviceid=" + agentserviceid));
    }
}
