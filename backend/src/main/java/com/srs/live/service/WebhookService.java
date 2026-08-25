package com.srs.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srs.live.entity.ThirdPartyApp;
import com.srs.live.mapper.ThirdPartyAppMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

@Slf4j
@Service
public class WebhookService {

    private final ThirdPartyAppMapper thirdPartyAppMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WebhookService(ThirdPartyAppMapper thirdPartyAppMapper, ObjectMapper objectMapper) {
        this.thirdPartyAppMapper = thirdPartyAppMapper;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public void fireEvent(String eventType, Object payload) {
        LambdaQueryWrapper<ThirdPartyApp> queryWrapper = new LambdaQueryWrapper<>();
        List<ThirdPartyApp> apps = thirdPartyAppMapper.selectList(
                queryWrapper.eq(ThirdPartyApp::getStatus, 1));
        for (ThirdPartyApp app : apps) {
            if (app.getWebhookUrl() == null || app.getWebhookEvents() == null) continue;
            if (!app.getWebhookEvents().contains(eventType)) continue;

            try {
                String payloadStr = objectMapper.writeValueAsString(payload);
                String signature = hmacSha256(app.getSecretKey(), payloadStr);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Signature", signature);

                HttpEntity<String> entity = new HttpEntity<>(payloadStr, headers);
                restTemplate.exchange(app.getWebhookUrl(), HttpMethod.POST, entity, String.class);
                log.info("webhook sent: event={}, app={}", eventType, app.getAppId());
            } catch (Exception e) {
                log.error("webhook failed: event={}, app={}", eventType, app.getAppId(), e);
            }
        }
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(spec);
            byte[] bytes = mac.doFinal(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("hmac error", e);
            return "";
        }
    }
}