package com.claubloom.harness.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体工具注册表，提供动态、线程安全的工具注册、检索与生命周期管理。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry() {
    }

    public ToolRegistry(List<ToolDefinition> initialTools) {
        if (initialTools != null) {
            for (ToolDefinition tool : initialTools) {
                register(tool);
            }
        }
    }

    /**
     * 注册新的工具定义到注册表中。
     */
    public void register(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        Objects.requireNonNull(tool.name(), "tool name must not be null");
        tools.put(tool.name(), tool);
        log.debug("Registered tool: {}", tool.name());
    }

    /**
     * 根据工具名称注销已有工具。
     */
    public Optional<ToolDefinition> unregister(String name) {
        if (name == null) {
            return Optional.empty();
        }
        ToolDefinition removed = tools.remove(name);
        if (removed != null) {
            log.debug("Unregistered tool: {}", name);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * 根据唯一名称查找工具定义。
     */
    public Optional<ToolDefinition> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 检查指定名称的工具是否已注册。
     */
    public boolean contains(String name) {
        return name != null && tools.containsKey(name);
    }

    /**
     * 获取所有已注册工具的不可修改集合。
     */
    public Collection<ToolDefinition> list() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 当前已注册的工具总数。
     */
    public int size() {
        return tools.size();
    }
}
