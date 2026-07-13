package me.rainhouse.qasystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class ModelPathHealthChecker implements ApplicationRunner {

    private final LocalModelServiceProperties localModelServiceProperties;
    private final RestTemplate restTemplate;

    public ModelPathHealthChecker(LocalModelServiceProperties localModelServiceProperties) {
        this.localModelServiceProperties = localModelServiceProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!localModelServiceProperties.isEnabled()) {
            log.info("本地模型服务未启用");
            return;
        }

        String baseUrl = localModelServiceProperties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            log.warn("本地模型服务地址未配置");
            return;
        }

        String healthUrl = baseUrl.replaceAll("/+$", "") + "/health";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("本地模型服务已就绪: {}", healthUrl);
            } else {
                log.warn("本地模型服务健康检查失败: {}，HTTP {}", healthUrl, response.getStatusCode().value());
            }
        } catch (RestClientException ex) {
            log.warn("本地模型服务不可用: {}，{}", healthUrl, ex.getMessage());
        }
    }
}
