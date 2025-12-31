
package com.vgl.cli;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class BuildSmokeTest {
  @Test void argsParses(){
    Args a=new Args(new String[]{"status","-vv"});
    assertThat(a.cmd).isEqualTo("status");
  }
}
