package com.srs.live.controller;

import com.srs.live.dto.request.CreateRoomRequest;
import com.srs.live.dto.request.UpdateRoomRequest;
import com.srs.live.dto.response.ApiResponse;
import com.srs.live.dto.response.RoomResponse;
import com.srs.live.dto.response.StartLiveResponse;
import com.srs.live.service.OnlineUserService;
import com.srs.live.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;
    private final OnlineUserService onlineUserService;

    public RoomController(RoomService roomService, OnlineUserService onlineUserService) {
        this.roomService = roomService;
        this.onlineUserService = onlineUserService;
    }

    @PostMapping
    public ApiResponse<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request,
                                                  HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        return ApiResponse.success(roomService.createRoom(request, uid));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(@PathVariable String roomId) {
        return ApiResponse.success(roomService.getRoom(roomId));
    }

    @GetMapping
    public ApiResponse<List<RoomResponse>> listRooms(@RequestParam(required = false) String status) {
        return ApiResponse.success(roomService.listRooms(status));
    }

    @PutMapping("/{roomId}")
    public ApiResponse<RoomResponse> updateRoom(@PathVariable String roomId,
                                                 @Valid @RequestBody UpdateRoomRequest request,
                                                 HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        return ApiResponse.success(roomService.updateRoom(roomId, request, uid));
    }

    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> closeRoom(@PathVariable String roomId, HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        roomService.closeRoom(roomId, uid);
        return ApiResponse.success();
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<StartLiveResponse> startLive(@PathVariable String roomId,
                                                     @RequestParam(defaultValue = "false") boolean recording,
                                                     HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        return ApiResponse.success(roomService.startLive(roomId, uid, recording));
    }

    @PostMapping("/{roomId}/stop")
    public ApiResponse<Void> stopLive(@PathVariable String roomId, HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        roomService.stopLive(roomId, uid);
        return ApiResponse.success();
    }

    @GetMapping("/{roomId}/users")
    public ApiResponse<List<Map<String, Object>>> getRoomUsers(@PathVariable String roomId) {
        return ApiResponse.success(onlineUserService.getRoomOnlineUsers(roomId));
    }

    @GetMapping("/{roomId}/play-url")
    public ApiResponse<String> getPlayUrl(@PathVariable String roomId, HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        return ApiResponse.success(roomService.getPlayUrl(roomId, uid));
    }

    @PostMapping("/{roomId}/kick/{targetUid}")
    public ApiResponse<Void> kickUser(@PathVariable String roomId,
                                       @PathVariable String targetUid,
                                       HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        // 校验权限：仅当前房间主播或管理员可踢人（由 RoomService 查数据库判断）
        var room = roomService.getRoomEntity(roomId);
        roomService.checkOwnerOrAdmin(room, uid);
        // 踢人逻辑：发送 WS 通知 + 清理在线状态
        onlineUserService.userOffline(targetUid);
        return ApiResponse.success();
    }
}