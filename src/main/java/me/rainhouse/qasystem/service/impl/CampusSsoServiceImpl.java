package me.rainhouse.qasystem.service.impl;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.common.dto.CampusSsoUserDTO;
import me.rainhouse.qasystem.service.CampusSsoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class CampusSsoServiceImpl implements CampusSsoService {

    @Autowired
    private RestTemplate restTemplate;

    // 从配置文件中读取一网通的 Token 校验地址
    @Value("${campus.sso.verify-url:}")
    private String ssoVerifyUrl;

    @Override
    public CampusSsoUserDTO verifyToken(String ssoToken) {
        if (!StringUtils.hasText(ssoVerifyUrl)) {
            log.warn("未配置大连东软信息学院的一网通验证地址(campus.sso.verify-url)，将使用环境模拟数据！");
            return mockSsoData(ssoToken);
        }

        try {
            // 真实情况：发往一网通服务器请求验证Token的合法性
            // 假设一网通验证接口 GET /api/verify?token=xxx
            String requestUrl = ssoVerifyUrl + "?token=" + ssoToken;
            ResponseEntity<Map> response = restTemplate.getForEntity(requestUrl, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                
                // 必须根据学校实际API文档的返回值进行字段对应解析，这里为标准范例
                CampusSsoUserDTO dto = new CampusSsoUserDTO();
                dto.setCampusSsoId(String.valueOf(body.get("ssoId")));
                dto.setUsername(String.valueOf(body.get("studentOrEmpNo")));
                dto.setRealName(String.valueOf(body.get("name")));
                dto.setIdentityType(String.valueOf(body.get("type"))); // e.g., "STUDENT" / "TEACHER"
                return dto;
            } else {
                throw new RuntimeException("一网通Token验证失败，服务器响应异常");
            }
        } catch (Exception e) {
            log.error("大连东软一网通验证异常: ", e);
            throw new RuntimeException("一网通登录授权校验失败");
        }
    }

    /**
     * 挡板Mock数据：用于本地开发和未联网调试
     */
    private CampusSsoUserDTO mockSsoData(String ssoToken) {
        CampusSsoUserDTO dto = new CampusSsoUserDTO();
        dto.setCampusSsoId("SSO_" + UUID.randomUUID().toString().substring(0, 8));
        dto.setRealName("东软一网通测试用户");
        // 随机生成学号
        dto.setUsername("2026" + (int)((Math.random() * 9 + 1) * 10000));
        dto.setIdentityType("STUDENT");
        return dto;
    }
}
