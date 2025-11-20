package com.vgl.cli;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class ArgsTest {
    @Test
    void parsesCommandAndArgs() {
        Args args = new Args(new String[]{"status", "-vv"});
        assertThat(args.cmd).isEqualTo("status");
        assertThat(args.rest).containsExactly("-vv");
    }
}
