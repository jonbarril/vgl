
package com.vgl.cli;
import java.util.*;
class Args {
  final String cmd; final List<String> rest;
  Args(String[] argv){
    if(argv==null || argv.length==0){ this.cmd="help"; this.rest=List.of(); }
    else { this.cmd=argv[0].toLowerCase(Locale.ROOT); this.rest=List.of(argv).subList(1, argv.length); }
  }
}
