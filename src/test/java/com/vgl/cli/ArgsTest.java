
package com.vgl.cli;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class ArgsTest {
  @Test void parsesCommandAndArgs(){
    Args a=new Args(new String[]{"status","-vv"});
    assertThat(a.cmd).isEqualTo("status");
    assertThat(a.rest).containsExactly("-vv");
  }
}
