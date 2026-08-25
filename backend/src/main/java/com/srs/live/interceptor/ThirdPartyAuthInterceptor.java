package com.srs.live.interceptor;

import com.srs.live.common.exception.AuthException;
import com.srs.live.service.ThirdPartyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

@Component
public class ThirdPartyAuthInterceptor implements HandlerInterceptor {

    private final ThirdPartyService thirdPartyService;
    private final boolean authEnabled;
    private final long timestampTolerance;

    public ThirdPartyAuthInterceptor(ThirdPartyService thirdPartyService,
                                     @Value("${srs.third-party.auth-enabled:true}") boolean authEnabled,
                                     @Value("${srs.third-party.timestamp-tolerance:300000}") long timestampTolerance) {
        this.thirdPartyService = thirdPartyService;
        this.authEnabled = authEnabled;
        this.timestampTolerance = timestampTolerance;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authEnabled) return true;

        String accessKey = request.getHeader("X-Access-Key");
        String signature = request.getHeader("X-Signature");
        String timestamp = request.getHeader("X-Timestamp");

        if (accessKey == null || signature == null || timestamp == null) {
            throw new AuthException("missing auth headers");
        }

        // 校验时间戳（防重放）
        long ts = Long.parseLong(timestamp);
        if (Math.abs(System.currentTimeMillis() - ts) > timestampTolerance) {
            throw new AuthException("request expired");
        }

        // 查询 AK 对应的 SK
        String secretKey = thirdPartyService.getSecretKey(accessKey);
        if (secretKey == null) {
            throw new AuthException("invalid access key");
        }

        // 校验签名
        String expectedSign = hmacSha256(secretKey, timestamp + request.getRequestURI());
        if (!MessageDigest.isEqual(expectedSign.getBytes(), signature.getBytes())) {
            throw new AuthException("invalid signature");
        }

        return true;
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
            throw new AuthException("signature error");
        }
    }
}