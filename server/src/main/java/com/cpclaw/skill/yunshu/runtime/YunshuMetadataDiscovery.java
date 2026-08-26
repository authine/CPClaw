package com.cpclaw.skill.yunshu.runtime;

/** Resolves an executable metadata object and its safe execution hints. */
public interface YunshuMetadataDiscovery {
    YunshuDiscovery discover(String goal);
}
