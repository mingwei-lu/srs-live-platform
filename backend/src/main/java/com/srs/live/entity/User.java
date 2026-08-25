package com.srs.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String uid;
    private String phone;
    private String username;
    private String company;
    private String department;
    private String phoneTail;
    private String passwordHash;
    private String role;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 拼接显示名 */
    public String getDisplayName() {
        if (username != null && !username.isEmpty()) {
            StringBuilder sb = new StringBuilder(username);
            boolean hasCompany = company != null && !company.isEmpty();
            boolean hasDept = department != null && !department.isEmpty();
            boolean hasPhone = phoneTail != null && !phoneTail.isEmpty();
            if (hasCompany || hasDept || hasPhone) {
                sb.append(" (");
                if (hasCompany) sb.append(company);
                if (hasDept) sb.append(hasCompany ? " " : "").append(department);
                if (hasPhone) sb.append(" ").append(phoneTail);
                sb.append(")");
            }
            return sb.toString();
        }
        // 没有昵称时用手机号脱敏显示
        if (phone != null && phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return phone != null ? phone : uid;
    }
}