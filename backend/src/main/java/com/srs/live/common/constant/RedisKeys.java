package com.srs.live.common.constant;

public class RedisKeys {
    private RedisKeys() {}

    public static final String ONLINE_USERS = "online:users";
    public static final String ROOM_USERS = "room:%s:users";
    public static final String USER_NODE = "user:%s:node";
    public static final String USER_HEARTBEAT = "user:%s:heartbeat";
    public static final String USER_OFFLINE_MSGS = "user:%s:offline_msgs";
    public static final String WS_NODE_USERS = "ws:node:%s:users";
    public static final String SRS_CLUSTER_NODES = "srs:cluster:nodes";
    public static final String SRS_NODE_CONNECTIONS = "srs:node:%s:connections";
    public static final String API_RATE = "api:rate:%s";
    public static final String API_TOKEN = "api:token:%s";

    public static String roomUsers(String roomId) { return String.format(ROOM_USERS, roomId); }
    public static String userNode(String uid) { return String.format(USER_NODE, uid); }
    public static String userHeartbeat(String uid) { return String.format(USER_HEARTBEAT, uid); }
    public static String userOfflineMsgs(String uid) { return String.format(USER_OFFLINE_MSGS, uid); }
    public static String wsNodeUsers(String nodeId) { return String.format(WS_NODE_USERS, nodeId); }
    public static String srsNodeConnections(String nodeId) { return String.format(SRS_NODE_CONNECTIONS, nodeId); }
    public static String apiRate(String appId) { return String.format(API_RATE, appId); }
    public static String apiToken(String appId) { return String.format(API_TOKEN, appId); }
}