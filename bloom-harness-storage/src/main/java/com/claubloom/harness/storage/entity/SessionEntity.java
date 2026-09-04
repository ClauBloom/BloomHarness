package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity mapped to SQLite 'sessions' table.
 * Directly mirrors pi's 001_initial.sql sessions schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sessions")
public class SessionEntity {

    @TableId
    private String id;

    private Long createdAt;

    private String cwd;

    private String parentSessionId;

    private String metadata;
}
