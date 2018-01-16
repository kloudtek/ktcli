package com.kloudtek.ktcli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
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

    protected void init(CliHelper cli, CommandLine commandLine, T parent) {
        this.cli = cli;
        this.commandLine = commandLine;
        this.parent = parent;
    }

    public CommandLine getCommandLine() {
        return commandLine;
    }

    protected void loadConfig(@NotNull ObjectNode cfg) throws Exception {
        this.config = cfg;
        CliHelper.getObjectMapper().readerForUpdating(this).readValue(config);
    }

    protected void saveConfig() {
        ObjectNode jsonNode = CliHelper.getObjectMapper().valueToTree(this);
        if (config != null) {
            config.setAll(jsonNode);
        } else {
            config = jsonNode;
        }
    }

    public T getParent() {
        return parent;
    }

    public List<CliCommand<?>> getExtraSubCommands() {
        return Collections.emptyList();
    }

    protected void execute() throws Exception {
        cli.printUsage(this);
    }
}
