package com.srs.live.common.enums;

public enum RoomStatus {
    WAITING("waiting", "等待中"),
    LIVE("live", "直播中"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String desc;

    RoomStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static RoomStatus fromCode(String code) {
        for (RoomStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return WAITING;
    }
}