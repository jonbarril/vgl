package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

public class VglCliTest {
	@Test
	@Timeout(10)
	void helpRunsQuickly() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream oldOut = System.out;
		PrintStream oldErr = System.err;
		try {
			PrintStream ps = new PrintStream(baos, true, "UTF-8");
			System.setOut(ps);
			System.setErr(ps);
			int rc = new VglCli().run(new String[] { "help" });
			String out = baos.toString("UTF-8");
			assertThat(rc).isZero();
			assertThat(out).contains("Commands:");
		} finally {
			System.setOut(oldOut);
			System.setErr(oldErr);
		}
	}
}