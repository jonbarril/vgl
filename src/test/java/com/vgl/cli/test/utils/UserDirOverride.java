package com.vgl.cli.test.utils;

import java.nio.file.Path;

public final class UserDirOverride implements AutoCloseable {
    private final String prior;

    public UserDirOverride(Path userDir) {
        prior = System.getProperty("user.dir");
        System.setProperty("user.dir", userDir.toString());
    }

    @Override
    public void close() {
        if (prior == null) {
            System.clearProperty("user.dir");
        } else {
            System.setProperty("user.dir", prior);
        }
    }
}
