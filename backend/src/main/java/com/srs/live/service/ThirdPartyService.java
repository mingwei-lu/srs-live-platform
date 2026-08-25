package com.srs.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srs.live.entity.ThirdPartyApp;
import com.srs.live.mapper.ThirdPartyAppMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ThirdPartyService {

    private final ThirdPartyAppMapper thirdPartyAppMapper;

    public ThirdPartyService(ThirdPartyAppMapper thirdPartyAppMapper) {
        this.thirdPartyAppMapper = thirdPartyAppMapper;
    }

    public String getSecretKey(String accessKey) {
        LambdaQueryWrapper<ThirdPartyApp> lambdaQueryWrapper = new LambdaQueryWrapper<ThirdPartyApp>();
        ThirdPartyApp app = thirdPartyAppMapper.selectOne(
                lambdaQueryWrapper.eq(ThirdPartyApp::getAccessKey, accessKey));
        return app != null ? app.getSecretKey() : null;
    }

    public ThirdPartyApp getByAppId(String appId) {
        LambdaQueryWrapper<ThirdPartyApp> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        return thirdPartyAppMapper.selectOne(lambdaQueryWrapper.eq(ThirdPartyApp::getAppId, appId));
    }

    public ThirdPartyApp createApp(String appName) {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setAppId("app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        app.setAppName(appName);
        app.setAccessKey("ak_" + UUID.randomUUID().toString().replace("-", ""));
        app.setSecretKey("sk_" + UUID.randomUUID().toString().replace("-", ""));
        app.setStatus(1);
        thirdPartyAppMapper.insert(app);
        return app;
    }

    public List<ThirdPartyApp> listApps() {
        return thirdPartyAppMapper.selectList(null);
    }
}