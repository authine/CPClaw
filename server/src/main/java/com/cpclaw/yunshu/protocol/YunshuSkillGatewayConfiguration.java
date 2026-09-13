package com.cpclaw.yunshu.protocol;

import com.cpclaw.skill.yunshu.form.PersistentFormTaskRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects a safe no-write runtime until a persistent FormTask runtime is
 * explicitly registered. This prevents adapters from choosing legacy runtime
 * implementations or silently enabling writes.
 */
@Configuration
class YunshuSkillGatewayConfiguration {
    @Bean
    @ConditionalOnMissingBean({YunshuCanonicalTaskRuntime.class, PersistentFormTaskRuntime.class})
    YunshuCanonicalTaskRuntime contractGuardYunshuCanonicalTaskRuntime() {
        return new ContractGuardYunshuSkillGateway();
    }

    @Bean
    @ConditionalOnMissingBean({YunshuSkillGateway.class, PersistentFormTaskRuntime.class})
    YunshuSkillGateway yunshuSkillGateway(YunshuCanonicalTaskRuntime runtime) {
        return new DefaultYunshuSkillGateway(runtime);
    }

}
