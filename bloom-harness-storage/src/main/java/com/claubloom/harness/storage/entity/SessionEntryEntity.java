package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 映射到 SQLite 'entries' 表的实体。
 * 与 pi 的 001_initial.sql entries 表结构直接对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("entries")
public class SessionEntryEntity {

    private String sessionId;

    private Integer seq;

    @TableId
    private String id;

    private String parentId;

    private String type; // user_message, assistant_message, tool_result, compaction

    private Long timestamp;

    private String payload; // JSON 序列化后的消息
}
