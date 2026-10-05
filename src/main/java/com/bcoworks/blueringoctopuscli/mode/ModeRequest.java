package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.util.PathUtils;

public record ModeRequest(String prompt, String path) {

    public ModeRequest {
        prompt = prompt == null ? "" : prompt.strip();
        path = PathUtils.clean(path);
    }

    public boolean hasPath() {
        return !path.isEmpty();
    }
}