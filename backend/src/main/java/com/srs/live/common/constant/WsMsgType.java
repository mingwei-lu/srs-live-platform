package com.srs.live.common.constant;

public class WsMsgType {
    private WsMsgType() {}

    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final String JOIN_ROOM = "join_room";
    public static final String LEAVE_ROOM = "leave_room";
    public static final String ACK = "ack";
    public static final String ROOM_USER_LIST = "room_user_list";
    public static final String ROOM_STATUS_CHANGE = "room_status_change";
    public static final String KICK_NOTIFICATION = "kick_notification";
    public static final String RECONNECT_COMMAND = "reconnect_command";
}