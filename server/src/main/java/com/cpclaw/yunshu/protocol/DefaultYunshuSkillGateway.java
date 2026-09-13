package com.cpclaw.yunshu.protocol;

/** Gateway delegates to the single server-selected canonical runtime. */
final class DefaultYunshuSkillGateway implements YunshuSkillGateway {
    private final YunshuCanonicalTaskRuntime runtime;

    DefaultYunshuSkillGateway(YunshuCanonicalTaskRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public YunshuTaskEnvelope handle(YunshuTaskRequest request, YunshuHostContext hostContext) {
        return runtime.execute(request, hostContext);
    }
}
