package com.kloudtek.ktcli;

import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(showDefaultValues = true,requiredOptionMarker = '*')
public class TestCmdWithRequireField extends CliCommand<CliCommand> {
    @Option(names = "-val",required = true)
    public String someval;
}
