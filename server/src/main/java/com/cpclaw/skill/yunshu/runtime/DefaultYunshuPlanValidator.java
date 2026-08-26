package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.skill.yunshu.runtime.MetadataExecutionPlanner.MetadataExecutionPlan;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Generic validation gate; no business object or field vocabulary is used. */
@Component
public final class DefaultYunshuPlanValidator implements YunshuPlanValidator {
    @Override
    public YunshuPlanValidation validate(MetadataExecutionPlan plan, String intent, YunshuExecutionScope scope) {
        if (plan == null || !plan.executable() || plan.executableMatch() == null
                || plan.executableMatch().code() == null || plan.executableMatch().code().isBlank()) {
            return YunshuPlanValidation.blocked("元数据计划不可执行或缺少已验证对象。");
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("对象和执行编码来自已同步元数据");
        if (scope == null || scope.connection() == null) {
            return YunshuPlanValidation.blocked("缺少已认证的云枢执行范围。");
        }
        boolean write = intent != null && List.of("create_data", "update_data", "delete_data").contains(intent);
        if (write && !scope.allowWrites()) {
            return new YunshuPlanValidation("confirmation_required", false, true,
                List.of("当前执行范围为只读，写操作必须经过可信确认。"));
        }
        if ("workflow_query".equals(intent) && plan.apiHints().isEmpty()) {
            return YunshuPlanValidation.blocked("未找到已登记的流程读取契约。");
        }
        return new YunshuPlanValidation("valid", true, false, reasons);
    }
}
