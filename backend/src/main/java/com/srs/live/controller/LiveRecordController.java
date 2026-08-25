package com.srs.live.controller;

import com.srs.live.dto.response.ApiResponse;
import com.srs.live.entity.LiveParticipant;
import com.srs.live.entity.LiveRecord;
import com.srs.live.service.LiveRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live-records")
public class LiveRecordController {

    private final LiveRecordService liveRecordService;

    public LiveRecordController(LiveRecordService liveRecordService) {
        this.liveRecordService = liveRecordService;
    }

    /**
     * 获取房间的直播历史列表
     */
    @GetMapping
    public ApiResponse<List<LiveRecord>> getRoomRecords(@RequestParam String roomId) {
        return ApiResponse.success(liveRecordService.getRoomRecords(roomId));
    }

    /**
     * 获取单个直播记录详情
     */
    @GetMapping("/{recordId}")
    public ApiResponse<LiveRecord> getRecord(@PathVariable Long recordId) {
        return ApiResponse.success(liveRecordService.getRecordById(recordId));
    }

    /**
     * 获取某场直播的参与者列表
     */
    @GetMapping("/{recordId}/participants")
    public ApiResponse<List<LiveParticipant>> getParticipants(@PathVariable Long recordId) {
        return ApiResponse.success(liveRecordService.getParticipants(recordId));
    }
}
