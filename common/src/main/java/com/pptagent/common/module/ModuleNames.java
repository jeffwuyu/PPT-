package com.pptagent.common.module;

import java.util.List;

public final class ModuleNames {

    public static final List<String> FOUNDATION_MODULES = List.of(
            "api-server",
            "common",
            "domain",
            "infrastructure",
            "agent-core",
            "ppt-renderer",
            "collab-server"
    );

    private ModuleNames() {
    }
}
