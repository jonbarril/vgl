package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ArgsParsingTest {

    @Test
    void argsExtractsCommand() {
        Args args = new Args(new String[]{"status", "-v"});
        assertThat(args.cmd).isEqualTo("status");
    }

    @Test
    void argsExtractsFlags() {
        Args args = new Args(new String[]{"status", "-v", "-vv"});
        assertThat(args.rest).contains("-v", "-vv");
    }

    @Test
    void argsHandlesNoCommand() {
        Args args = new Args(new String[]{});
        assertThat(args.cmd).isEqualTo("help"); // Defaults to help
        assertThat(args.rest).isEmpty();
    }

    @Test
    void argsPreservesOrder() {
        Args args = new Args(new String[]{"diff", "file1", "file2", "-lb"});
        assertThat(args.cmd).isEqualTo("diff");
        assertThat(args.rest).containsExactly("file1", "file2", "-lb");
    }
}
