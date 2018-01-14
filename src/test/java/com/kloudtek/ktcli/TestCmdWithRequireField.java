package com.kloudtek.ktcli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "testcmd", showDefaultValues = true,requiredOptionMarker = '*')
public class TestCmdWithRequireField extends CliCommand<CliCommand> {
    @Option(names = "-val",required = true)
    public String someval;
}
