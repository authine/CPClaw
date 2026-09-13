package com.cpclaw.yunshu.protocol;

/**
 * Execution seam behind the protocol Gateway. The persistent FormTask runtime
 * implements this interface in WP3/WP4; adapters never select an executor.
 */
public interface YunshuCanonicalTaskRuntime {
    YunshuTaskEnvelope execute(YunshuTaskRequest request, YunshuHostContext hostContext);
}
