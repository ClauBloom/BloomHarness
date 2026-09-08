package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 映射到 SQLite 'sessions' 表的实体。
 * 与 pi 的 001_initial.sql sessions 表结构直接对应。
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
