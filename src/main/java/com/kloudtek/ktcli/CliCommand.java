package com.kloudtek.ktcli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;

public abstract class CliCommand<T extends CliCommand> {
    protected CliHelper cli;
    protected CommandLine commandLine;
    protected ObjectNode config;
    protected T parent;

    public CliCommand() {
    }

    protected void init(CliHelper cli, CommandLine commandLine, T parent, ObjectNode cfg) {
        this.cli = cli;
        this.commandLine = commandLine;
        this.parent = parent;
        config = cfg;
    }

    protected void loadConfig() throws Exception {
        if (config != null) {
            CliHelper.getObjectMapper().readerForUpdating(this).readValue(config);
        }
    }

    protected void saveConfig() {
        ObjectNode jsonNode = CliHelper.getObjectMapper().valueToTree(this);
        config.setAll(jsonNode);
    }

    public T getParent() {
        return parent;
    }

    public List<CliCommand<?>> getExtraSubCommands() {
        return Collections.emptyList();
    }

    protected void execute() throws Exception {
        commandLine.usage(System.out);
    }
}
