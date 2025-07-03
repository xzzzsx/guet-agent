package com.atguigu.guliai.enums;

import cn.hutool.core.util.EnumUtil;
import lombok.Getter;

@Getter
public enum AgentTypeEnum {
    ROUTE("ROUTE", "路由智能体"),
    RECOMMEND("RECOMMEND", "课程推荐智能体"),
    RESERVATION("RESERVATION", "课程预约智能体"),
    SCHOOL_QUERY("SCHOOL_QUERY", "校区查询智能体"),
    MAPS_QUERY("MAPS_QUERY", "地图查询智能体"); // 新增地图查询智能体

    private final String agentName;
    private final String desc;

    AgentTypeEnum(String agentName, String desc) {
        this.agentName = agentName;
        this.desc = desc;
    }

    public static AgentTypeEnum agentNameOf(String agentName) {
        return EnumUtil.getBy(AgentTypeEnum::getAgentName, agentName);
    }
}