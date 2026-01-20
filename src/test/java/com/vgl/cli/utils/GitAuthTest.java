package com.vgl.cli.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitAuthTest {

    @Test
    void isMissingCredentialsProvider_detectsMessageInCauseChain() {
        RuntimeException root = new RuntimeException(
            "Authentication is required but no CredentialsProvider has been registered"
        );
        RuntimeException outer = new RuntimeException("top", root);

        assertThat(GitAuth.isMissingCredentialsProvider(outer)).isTrue();
    }

    @Test
    void isMissingCredentialsProvider_returnsFalseForOtherErrors() {
        RuntimeException e = new RuntimeException("something else");
        assertThat(GitAuth.isMissingCredentialsProvider(e)).isFalse();
    }
}
