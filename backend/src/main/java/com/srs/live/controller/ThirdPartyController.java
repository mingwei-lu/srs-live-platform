package com.srs.live.controller;

import com.srs.live.dto.response.ApiResponse;
import com.srs.live.entity.ThirdPartyApp;
import com.srs.live.service.ThirdPartyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/third-party")
public class ThirdPartyController {

    private final ThirdPartyService thirdPartyService;

    public ThirdPartyController(ThirdPartyService thirdPartyService) {
        this.thirdPartyService = thirdPartyService;
    }

    @PostMapping("/apps")
    public ApiResponse<ThirdPartyApp> createApp(@RequestParam String appName) {
        return ApiResponse.success(thirdPartyService.createApp(appName));
    }

    @GetMapping("/apps")
    public ApiResponse<List<ThirdPartyApp>> listApps() {
        return ApiResponse.success(thirdPartyService.listApps());
    }
}