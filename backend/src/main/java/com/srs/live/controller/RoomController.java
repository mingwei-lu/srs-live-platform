package com.srs.live.controller;

import com.srs.live.dto.request.CreateRoomRequest;
import com.srs.live.dto.request.UpdateRoomRequest;
import com.srs.live.dto.response.ApiResponse;
import com.srs.live.dto.response.RoomResponse;
import com.srs.live.dto.response.RoomUserListResponse;
import com.srs.live.dto.response.StartLiveResponse;
import com.srs.live.service.OnlineUserService;
import com.srs.live.service.RoomService;
import com.srs.live.service.WebSocketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;
    private final OnlineUserService onlineUserService;
    private final WebSocketService webSocketService;

    public RoomController(RoomService roomService, OnlineUserService onlineUserService,
                          WebSocketService webSocketService) {
        this.roomService = roomService;
        this.onlineUserService = onlineUserService;
        this.webSocketService = webSocketService;
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
                                                    @RequestParam(defaultValue = "false") boolean serverRecording,
                                                    HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        return ApiResponse.success(roomService.startLive(roomId, uid, serverRecording));
    }

    @PostMapping("/{roomId}/stop")
    public ApiResponse<Void> stopLive(@PathVariable String roomId, HttpServletRequest httpReq) {
        String uid = (String) httpReq.getAttribute("uid");
        roomService.stopLive(roomId, uid);
        return ApiResponse.success();
    }

    @GetMapping("/{roomId}/users")
    public ApiResponse<RoomUserListResponse> getRoomUsers(@PathVariable String roomId) {
        return ApiResponse.success(onlineUserService.getRoomOnlineUsersCategorized(roomId));
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
        var room = roomService.getRoomEntity(roomId);
        roomService.checkOwnerOrAdmin(room, uid);
        // 发送 WS 踢出通知 + 清理在线状态
        webSocketService.sendKickNotification(targetUid, "您已被踢出房间", uid);
        onlineUserService.userOffline(targetUid);
        // 广播更新后的房间用户列表
        var users = onlineUserService.getRoomOnlineUsersCategorized(roomId);
        webSocketService.broadcastRoomUserList(roomId, users);
        return ApiResponse.success();
    }
}
