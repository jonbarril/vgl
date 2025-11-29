
package com.vgl.cli;
import java.util.*;

class Args {
  final String cmd; 
  final List<String> rest;
  
  Args(String[] argv){
    if(argv==null || argv.length==0){ this.cmd="help"; this.rest=List.of(); }
    else { this.cmd=argv[0].toLowerCase(Locale.ROOT); this.rest=List.of(argv).subList(1, argv.length); }
  }
  
  /**
   * Get value for a flag. Returns null if flag not present or no value follows.
   * Example: getFlag(args, "-lb") for "vgl create -lb feature" returns "feature"
   */
  public static String getFlag(List<String> args, String flag) {
    int index = args.indexOf(flag);
    if (index != -1 && index + 1 < args.size()) {
      return args.get(index + 1);
    }
    return null;
  }
  
  /**
   * Check if a flag is present (for flags without values like -bb).
   * Example: hasFlag(args, "-bb") returns true if -bb is in the args
   */
  public static boolean hasFlag(List<String> args, String flag) {
    return args.contains(flag);
  }
  
  /**
   * Get first non-flag argument (doesn't start with -).
   * Useful for positional args during transition period.
   */
  public static String getPositional(List<String> args) {
    for (String arg : args) {
      if (!arg.startsWith("-")) {
        return arg;
      }
    }
    return null;
  }
}
