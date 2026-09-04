package com.claubloom.harness.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claubloom.harness.storage.entity.SessionEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SessionEntryEntity 的 MyBatis 数据库操作 Mapper 接口。
 */
@Mapper
public interface SessionEntryMapper extends BaseMapper<SessionEntryEntity> {

    @Select("SELECT COALESCE(MAX(seq), 0) + 1 FROM entries WHERE session_id = #{sessionId}")
    int getNextSeq(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM entries WHERE session_id = #{sessionId} ORDER BY seq ASC")
    List<SessionEntryEntity> findBySessionIdOrdered(@Param("sessionId") String sessionId);
}
