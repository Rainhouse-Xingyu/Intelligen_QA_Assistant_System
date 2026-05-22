package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import me.rainhouse.qasystem.common.utils.JwtUtils;
import me.rainhouse.qasystem.entity.SysUser;
import me.rainhouse.qasystem.service.SysUserService;
import me.rainhouse.qasystem.service.WechatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;

@Slf4j
@Service
public class WechatServiceImpl implements WechatService {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private WxMpService wxMpService;

    @Override
    public WxMpXmlOutMessage routeMessage(WxMpXmlMessage inMessage) {
        String msgType = inMessage.getMsgType();
        String fromUser = inMessage.getFromUser();

        // 默认回复
        String replyContent = "您好，您发送的消息系统正在处理中。";

        try {
            if (WxConsts.XmlMsgType.TEXT.equals(msgType)) {
                // 如果是文本消息，可以将 inMessage.getContent() 发送给 Coze AI 意图网关获取大模型答复
                String userContent = inMessage.getContent();
                log.info("收到微信用户 [{}] 的文本消息：{}", fromUser, userContent);
                
                // TODO: 这里之后会调用 2.1 Coze意图网关 并返回推理结果，先写个回显
                replyContent = "AI助手回复: 您说的是「" + userContent + "」吗？我们正在对大模型接口进行对接...";

            } else if (WxConsts.XmlMsgType.EVENT.equals(msgType)) {
                String eventType = inMessage.getEvent();
                if (WxConsts.EventType.SUBSCRIBE.equals(eventType)) {
                    replyContent = "欢迎关注智能教务助手！我们不仅能回答您的问题，还可以为您提供学业帮扶。";
                }
            } else if (WxConsts.XmlMsgType.VOICE.equals(msgType)) {
                // TODO: 支持 2.2 语音模态处理
                String recognition = inMessage.getRecognition();
                replyContent = "听到您的语音内容：" + (recognition != null ? recognition : "无法识别") + "\n正在向大模型网关查询...";
            }
        } catch (Exception e) {
            log.error("微信消息路由处理异常", e);
            replyContent = "抱歉，系统开小差了，请稍候再试。";
        }

        // 构建文本回复
        return WxMpXmlOutMessage.TEXT()
                .content(replyContent)
                .fromUser(inMessage.getToUser())
                .toUser(inMessage.getFromUser())
                .build();
    }

    @Override
    public String wechatLogin(String openId, me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken accessToken) {
        // 根据 openId 查找绑定用户
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("wechat_openid", openId);
        SysUser user = sysUserService.getOne(queryWrapper);

        if (user == null) {
            // 用户未绑定过，静默注册或者要求绑定已有账号。这里做静默注册。
            user = new SysUser();
            user.setUsername("wx_" + System.currentTimeMillis());
            user.setWechatOpenid(openId);
            user.setRole(1); // 默认学生
            
            try {
                // 尝试获取微信用户昵称与头像
                me.chanjar.weixin.common.bean.WxOAuth2UserInfo wxMpUser = wxMpService.getOAuth2Service().getUserInfo(accessToken, "zh_CN");
                if (wxMpUser != null) {
                    user.setRealName(wxMpUser.getNickname());
                    user.setAvatarUrl(wxMpUser.getHeadImgUrl());
                }
            } catch (Exception e) {
                log.warn("获取微信用户信息失败, 可能是权限不足, openId={}", openId);
            }
            
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            sysUserService.save(user);
        }

        // 生成系统业务的 JWT Token 给小程序或H5页面去使用
        return JwtUtils.generateToken(user.getId(), user.getRole());
    }
}
