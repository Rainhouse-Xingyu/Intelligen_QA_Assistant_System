package me.rainhouse.qasystem.service;

import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;

public interface WechatService {

    /**
     * 处理微信回调的消息/事件
     */
    WxMpXmlOutMessage routeMessage(WxMpXmlMessage inMessage);

    /**
     * 微信用户登录/注册
     * @param openId 微信的 OpenId
     * @param accessToken (可选)可以用于获取用户详情
     * @return 业务系统的 JWT Token
     */
    String wechatLogin(String openId, me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken accessToken);
}
