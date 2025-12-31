package com.vgl.cli.commands;

import java.util.List;

public interface Command {
    String name();
    int run(List<String> args) throws Exception;
}
