package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ConfigTest {

    @Test
    void vglCliLoadsDefaultConfig() {
        VglCli vgl = new VglCli();
        
        // Should have default values
        assertThat(vgl.getLocalDir()).isNotNull();
        assertThat(vgl.getLocalBranch()).isNotNull(); // Loads from .vgl or defaults to "main"
    }

    @Test
    void vglCliCanSetLocalDir() {
        VglCli vgl = new VglCli();
        vgl.setLocalDir("/test/path");
        
        // Path is normalized to absolute
        assertThat(vgl.getLocalDir()).endsWith("test\\path");
    }

    @Test
    void vglCliCanSetLocalBranch() {
        VglCli vgl = new VglCli();
        vgl.setLocalBranch("develop");
        
        assertThat(vgl.getLocalBranch()).isEqualTo("develop");
    }

    @Test
    void vglCliCanSetRemoteUrl() {
        VglCli vgl = new VglCli();
        vgl.setRemoteUrl("https://github.com/user/repo.git");
        
        assertThat(vgl.getRemoteUrl()).isEqualTo("https://github.com/user/repo.git");
    }

    @Test
    void vglCliCanSetRemoteBranch() {
        VglCli vgl = new VglCli();
        vgl.setRemoteBranch("feature");
        
        assertThat(vgl.getRemoteBranch()).isEqualTo("feature");
    }
}
