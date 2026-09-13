package com.cpclaw.yunshu.protocol;

/** Adapter-neutral boundary implemented by MCP and HTTP gateways. */
public interface YunshuSkillGateway {
    YunshuTaskEnvelope handle(YunshuTaskRequest request, YunshuHostContext hostContext);
}
