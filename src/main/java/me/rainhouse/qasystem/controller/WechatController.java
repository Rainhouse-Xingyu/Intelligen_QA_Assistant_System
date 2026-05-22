package me.rainhouse.qasystem.controller;

import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.service.WechatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/wechat")
public class WechatController {

    @Autowired
    private WxMpService wxMpService;

    @Autowired
    private WechatService wechatService;

    /**
     * 微信认证开发者服务器
     */
    @GetMapping(value = "/portal", produces = "text/plain;charset=utf-8")
    public String authGet(
            @RequestParam(name = "signature", required = false) String signature,
            @RequestParam(name = "timestamp", required = false) String timestamp,
            @RequestParam(name = "nonce", required = false) String nonce,
            @RequestParam(name = "echostr", required = false) String echostr) {

        log.info("接收到来自微信服务器的认证消息：[{}, {}, {}, {}]", signature, timestamp, nonce, echostr);

        if (wxMpService.checkSignature(timestamp, nonce, signature)) {
            return echostr;
        }

        return "非法请求";
    }

    /**
     * 接收微信公众号用户的消息/事件
     */
    @PostMapping(value = "/portal", produces = "application/xml; charset=UTF-8")
    public String post(@RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {

        log.info("接收微信请求：[openid=[{}], [signature=[{}], encType=[{}], msgSignature=[{}], timestamp=[{}], nonce=[{}], requestBody=[\n{}\n] ",
                openid, signature, encType, msgSignature, timestamp, nonce, requestBody);

        if (!wxMpService.checkSignature(timestamp, nonce, signature)) {
            throw new IllegalArgumentException("非法请求，可能伪造的签名!");
        }

        String out = null;
        if (encType == null) {
            // 明文传输的消息
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromXml(requestBody);
            WxMpXmlOutMessage outMessage = wechatService.routeMessage(inMessage);
            if (outMessage == null) {
                return "";
            }
            out = outMessage.toXml();
        } else if ("aes".equalsIgnoreCase(encType)) {
            // aes加密的消息
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromEncryptedXml(requestBody, wxMpService.getWxMpConfigStorage(),
                    timestamp, nonce, msgSignature);
            WxMpXmlOutMessage outMessage = wechatService.routeMessage(inMessage);
            if (outMessage == null) {
                return "";
            }
            out = outMessage.toEncryptedXml(wxMpService.getWxMpConfigStorage());
        }

        log.debug("组装回复信息：{}", out);
        return out;
    }

    /**
     * 免密登录：OAuth2 回调接口
     * 前端通过微信授权带上 code 访问此接口换取系统的 Token
     */
    @GetMapping("/login")
    public Result<Map<String, String>> login(@RequestParam("code") String code) {
        try {
            // 1. 通过 code 获取 OAuth2 AccessToken（内部包含 openid）
            WxOAuth2AccessToken accessToken = wxMpService.getOAuth2Service().getAccessToken(code);
            String openId = accessToken.getOpenId();

            // 2. 将 openId 映射为本平台的 JWT token
            String sysToken = wechatService.wechatLogin(openId, accessToken);

            Map<String, String> data = new HashMap<>();
            data.put("token", sysToken);
            return Result.success(data);
        } catch (Exception e) {
            log.error("微信登录异常：", e);
            return Result.error("微信登录异常: " + e.getMessage());
        }
    }
}
