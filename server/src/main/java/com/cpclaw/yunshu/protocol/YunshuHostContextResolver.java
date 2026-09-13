package com.cpclaw.yunshu.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Authentication adapter boundary. The requested host id is only routing data:
 * a resolver must derive tenant and subject from verified server-side identity,
 * never from a command or an unverified header.
 */
public interface YunshuHostContextResolver {
    String hostId();

    Optional<YunshuHostContext> resolve(HttpServletRequest request);
}
