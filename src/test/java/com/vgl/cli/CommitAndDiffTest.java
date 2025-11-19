package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CommitAndDiffTest {

  private static String run(String... args) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream oldOut = System.out;
    try {
      System.setOut(new PrintStream(baos, true, "UTF-8"));
      new Vgl().run(args);
      return baos.toString("UTF-8");
    } finally {
      System.setOut(oldOut);
    }
  }

  @Test
  public void commitPrintsShortId_andDiffShowsChanges() throws Exception {
    Path tmp = Files.createTempDirectory("vgltest");
    // create repo
    new Vgl().run(new String[]{"create", tmp.toString()});
    // create file and track & commit
    Files.writeString(tmp.resolve("a.txt"), "hello\n");
    new Vgl().run(new String[]{"focus", tmp.toString()});
    new Vgl().run(new String[]{"track", "a.txt"});
    String out = run("commit", "initial");
    // first line should be a short hash (7 hex)
    String first = out.strip();
    assertThat(first).matches("[0-9a-fA-F]{7,40}");

    // modify file and diff should show something (no args defaults to -lb)
    Files.writeString(tmp.resolve("a.txt"), "hello\nworld\n", java.nio.file.StandardOpenOption.APPEND);
    new Vgl().run(new String[]{"focus", tmp.toString()});
    String d1 = run("diff");
    assertThat(d1).isNotBlank();

    // requesting -rb with no remote should still default to -lb (not error, and show diff)
    String d2 = run("diff", "-rb");
    assertThat(d2).isNotBlank();
  }
}
