package com.nuoxin.virtual.rep.api.service;

import com.nuoxin.virtual.rep.api.common.bean.PageResponseBean;
import com.nuoxin.virtual.rep.api.common.enums.ErrorEnum;
import com.nuoxin.virtual.rep.api.common.exception.BusinessException;
import com.nuoxin.virtual.rep.api.common.exception.FileFormatException;
import com.nuoxin.virtual.rep.api.common.service.BaseService;
import com.nuoxin.virtual.rep.api.dao.DoctorRepository;
import com.nuoxin.virtual.rep.api.dao.DrugUserRepository;
import com.nuoxin.virtual.rep.api.dao.MessageRepository;
import com.nuoxin.virtual.rep.api.entity.Doctor;
import com.nuoxin.virtual.rep.api.entity.DrugUser;
import com.nuoxin.virtual.rep.api.entity.Message;
import com.nuoxin.virtual.rep.api.enums.MessageTypeEnum;
import com.nuoxin.virtual.rep.api.enums.UserTypeEnum;
import com.nuoxin.virtual.rep.api.mybatis.MessageMapper;
import com.nuoxin.virtual.rep.api.utils.DateUtil;
import com.nuoxin.virtual.rep.api.utils.ExcelUtils;
import com.nuoxin.virtual.rep.api.utils.RegularUtils;
import com.nuoxin.virtual.rep.api.utils.StringFormatUtil;
import com.nuoxin.virtual.rep.api.web.controller.request.message.MessageRequestBean;
import com.nuoxin.virtual.rep.api.web.controller.request.vo.WechatMessageVo;
import com.nuoxin.virtual.rep.api.web.controller.response.message.MessageLinkmanResponseBean;
import com.nuoxin.virtual.rep.api.web.controller.response.message.MessageResponseBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

/**
 * 微信相关接口
 */
@Service
public class MessageService extends BaseService {

    private static final String DRUG_USER_NICKNAME = "我";
    private static final String filePath = "exceltemplate/wechatMessage.xls";
    private static final String filename = "wechatMessage.xls";

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private DrugUserRepository drugUserRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private MessageMapper messageMapper;

    public void downloadExcel(HttpServletResponse response) {
        response.setHeader("content-Type", "application/vnd.ms-excel");
        // 下载文件的默认名称
        response.setHeader("Content-Disposition", "attachment;filename=" + filename);
        response.setContentType("application/octet-stream");

        InputStream systemResourceAsStream = ClassLoader.getSystemResourceAsStream(filePath);
        System.out.println(systemResourceAsStream);
        byte[] buff = new byte[1024];
        BufferedInputStream bis = null;
        OutputStream os = null;
        try {
            os = response.getOutputStream();
            bis = new BufferedInputStream(systemResourceAsStream);
            int i = bis.read(buff);
            while (i != -1) {
                os.write(buff, 0, buff.length);
                os.flush();
                i = bis.read(buff);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new BusinessException(ErrorEnum.ERROR);
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new BusinessException(ErrorEnum.ERROR);
                }
            }
        }
    }

    /**
     * 导入微信聊天消息
     * @param file 消息的excel文件
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean importExcel(MultipartFile file, DrugUser drugUser) {
        boolean success = false;
        
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename)){
            throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR, "文件名称不能为空");
        }
        
        if (!originalFilename.endsWith(RegularUtils.EXTENSION_XLS) && !originalFilename.endsWith(RegularUtils.EXTENSION_XLSX)) {
            throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR);
        }

        String fileName = originalFilename.substring(0,originalFilename.lastIndexOf("."));
        boolean matcher = RegularUtils.isMatcher(RegularUtils.MATCH_TELEPHONE, fileName);
        if (!matcher){
            throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR, "文件名称输入不合法，请以医生的手机号命名");
        }

        String doctorTelephone = fileName;
        boolean matche = RegularUtils.isMatcher(RegularUtils.MATCH_TELEPHONE, doctorTelephone);
        if (!matche){
            throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR, "手机号输入有误，请检查是否是文本格式");
        }

        List<Message> list = new ArrayList<>();

        ExcelUtils<WechatMessageVo> excelUtils = new ExcelUtils<>(new WechatMessageVo());
        List<WechatMessageVo> wechatMessageVos = null;
        InputStream inputStream = null;
        try {
        	inputStream = file.getInputStream();
            wechatMessageVos = excelUtils.readFromFile(null, inputStream);
        } catch (Exception e) {
            logger.error("读取上传的excel文件失败。。", e);
            throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR);
        } finally {
        	if(inputStream != null) {
        		try {
					inputStream.close();
				} catch (IOException e) {
					logger.error("IOException", e);
				}
        	}
        }

        if (null == wechatMessageVos || wechatMessageVos.size() <= 0) {
            return false;
        }

        for (WechatMessageVo wechatMessageVo : wechatMessageVos) {
            if (null != wechatMessageVo) {
                String id = wechatMessageVo.getId();
                if (StringUtils.isEmpty(id)) {
                    continue;
                }

                Date wechatTimeDate = wechatMessageVo.getWechatTime();
                String wechatTime = DateUtil.getDateTimeString(wechatTimeDate);
                String wechatNickName = wechatMessageVo.getNickname();
                String wechatNumber = wechatMessageVo.getWechatNumber();
                String wechatMessageStatus = wechatMessageVo.getMessageStatus();
                String wechatMessageType = wechatMessageVo.getMessageType();
                String message = wechatMessageVo.getMessage();

                //判断数据库中是否存在该条数据
				Message findMessage = messageRepository.findTopByMessageTypeAndWechatNumberAndMessageTimeOrderByMessageTimeDesc(
						MessageTypeEnum.WECHAT.getMessageType(), wechatNumber, wechatTime);
                if (findMessage != null){
                    //数据库存在该条数据
                    continue;
                }

                int userType = 0;
                String nickname = "";
                String telephone = "";
                Long userId = 0L;

                Doctor doctor = doctorRepository.findTopByMobile(doctorTelephone);
                if (doctor == null) {
                    throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR);
                }

                Long drugUserId = drugUser.getId();
                Long doctorId = doctor.getId();
                if (wechatNickName == null) {
                    throw new FileFormatException(ErrorEnum.FILE_FORMAT_ERROR);
                }

                if (wechatNickName != null && DRUG_USER_NICKNAME.equals(wechatNickName)) {
                    userType = UserTypeEnum.DRUG_USER.getUserType();
                    nickname = drugUser.getName();
                    telephone = drugUser.getMobile();
                    userId = drugUserId;
                } else if (wechatNickName != null && !DRUG_USER_NICKNAME.equals(wechatNickName)) {
                    userType = UserTypeEnum.DOCTOR.getUserType();
                    nickname = doctor.getName();
                    telephone = doctor.getMobile();
                    userId = doctorId;
                }

                Message wechatMessage = new Message();
                wechatMessage.setUserId(userId);
                wechatMessage.setUserType(userType);
                wechatMessage.setNickname(nickname);
                wechatMessage.setDrugUserId(drugUserId);
                wechatMessage.setDoctorId(doctorId);
                wechatMessage.setWechatNumber(wechatNumber);
                wechatMessage.setTelephone(telephone);
                wechatMessage.setWechatMessageStatus(wechatMessageStatus);
                wechatMessage.setMessage(message);
                wechatMessage.setWechatMessageType(wechatMessageType);
                wechatMessage.setMessageType(MessageTypeEnum.WECHAT.getMessageType());
                wechatMessage.setMessageTime(wechatTime);
                wechatMessage.setCreateTime(new Date());
                list.add(wechatMessage);
            }
        }

        //批量保存微信聊天消息
        messageRepository.save(list);

        success = true;
        return success;
    }

    //mybatis的写法
    public PageResponseBean<MessageResponseBean> getMessageList(MessageRequestBean bean) {
        DrugUser drugUser = drugUserRepository.findFirstById(bean.getDrugUserId());
        String leaderPath = drugUser.getLeaderPath();
        if (leaderPath == null){
            leaderPath = "";
        }
        bean.setLeaderPath(leaderPath +"%");
        Integer page = bean.getPage();
        Integer pageSize = bean.getPageSize();
        //bean.setPage(page  * pageSize);
        bean.setCurrentSize(page  * pageSize);

        List<MessageResponseBean> messageList = null;
        Integer messageListCount = 0;
        Integer messageType = bean.getMessageType();
        if (messageType != null){
            if (messageType == MessageTypeEnum.IM.getMessageType() || messageType == MessageTypeEnum.WECHAT.getMessageType()){
                messageList = messageMapper.getMessageList(bean);
                messageListCount = messageMapper.getMessageListCount(bean);
            }

            if (messageType == MessageTypeEnum.EMAIL.getMessageType()){
                messageList = messageMapper.getEmailMessageList(bean);
                messageListCount = messageMapper.getEmailMessageListCount(bean);
            }
        }

        if (messageListCount ==null){
            messageListCount = 0;
        }
        PageResponseBean<MessageResponseBean> pageResponseBean = new PageResponseBean<>(bean, messageListCount, messageList);

        return pageResponseBean;
    }

    /**
     * 今日会话统计
     * @param bean
     * @return
     */
    public Map<String, Integer> getMessageCountList(MessageRequestBean bean) {
        Map<String, Integer> map = new HashMap<>();
        DrugUser drugUser = drugUserRepository.findFirstById(bean.getDrugUserId());
        String leaderPath = drugUser.getLeaderPath();
        if (leaderPath == null){
            leaderPath = "";
        }
        bean.setLeaderPath(leaderPath +"%");
        bean.setMessageType(MessageTypeEnum.WECHAT.getMessageType());
        Integer wechatCount = messageMapper.messageCount(bean);
        bean.setMessageType(MessageTypeEnum.IM.getMessageType());
        Integer imCount = messageMapper.messageCount(bean);
        Integer emailMessageCount = messageMapper.emailMessageCount(bean);
        if (emailMessageCount == null){
            emailMessageCount=0;
        }
        map.put("wechat", wechatCount);
        map.put("im", imCount);
        map.put("email",emailMessageCount);

        return map;
    }

    /**
     * 微信消息联系人(mybatis)
     * @return
     */
    public PageResponseBean<MessageLinkmanResponseBean> getMessageLinkmanList(MessageRequestBean bean) {
        DrugUser drugUser = drugUserRepository.findFirstById(bean.getDrugUserId());
        String leaderPath = drugUser.getLeaderPath();
        if (leaderPath == null){
            leaderPath = "";
        }
        bean.setLeaderPath(leaderPath+"%");
        Integer page = bean.getPage();
        Integer pageSize = bean.getPageSize();
        //bean.setPage(page * pageSize);
        bean.setCurrentSize(page * pageSize);
        List<MessageLinkmanResponseBean> messageLinkmanList = messageMapper.getMessageLinkmanList(bean);
        if (null != messageLinkmanList && !messageLinkmanList.isEmpty()){
            for (MessageLinkmanResponseBean messageLinkmanResponseBean:messageLinkmanList){
                Integer messageType = messageLinkmanResponseBean.getMessageType();
                if (messageType != null){
                    if (messageType == MessageTypeEnum.WECHAT.getMessageType() || messageType == MessageTypeEnum.IM.getMessageType()){
                        String lastMessage = messageMapper.getLastMessage(messageType, messageLinkmanResponseBean.getDoctorId(), messageLinkmanResponseBean.getLastTime());
                        messageLinkmanResponseBean.setLastMessage(lastMessage);
                    }

                    if (messageType == MessageTypeEnum.EMAIL.getMessageType()){
                        String lastEmailMessage = messageMapper.getLastEmailMessage(messageLinkmanResponseBean.getDoctorId(), messageLinkmanResponseBean.getLastTime());
                        messageLinkmanResponseBean.setLastMessage(lastEmailMessage);
                    }
                }
            }
        }

        Integer messageLinkmanListCount = messageMapper.getMessageLinkmanListCount(bean);
        PageResponseBean<MessageLinkmanResponseBean> pageResponseBean = new PageResponseBean<>(bean,messageLinkmanListCount, messageLinkmanList);

        return pageResponseBean;
    }

    public void test() {
        List<Message> messageList = messageRepository.test();
        System.out.println(messageList.size());
        System.out.println(messageList);
    }

}
