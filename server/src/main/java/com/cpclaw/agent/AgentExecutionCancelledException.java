package com.cpclaw.agent;

public class AgentExecutionCancelledException extends RuntimeException {

    public AgentExecutionCancelledException() {
        super("本次执行已由用户中止");
    }
}
