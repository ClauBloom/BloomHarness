package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity mapped to SQLite 'entries' table.
 * Directly mirrors pi's 001_initial.sql entries schema.
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

    private String payload; // JSON serialized message
}
