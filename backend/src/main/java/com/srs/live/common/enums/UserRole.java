package com.srs.live.common.enums;

public enum UserRole {
    USER("user", "普通用户"),
    ADMIN("admin", "管理员");

    private final String code;
    private final String desc;

    UserRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static UserRole fromCode(String code) {
        for (UserRole r : values()) {
            if (r.code.equals(code)) return r;
        }
        return USER;
    }
}