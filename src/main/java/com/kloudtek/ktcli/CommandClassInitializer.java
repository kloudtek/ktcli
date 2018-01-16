package com.kloudtek.ktcli;

public interface CommandClassInitializer<T extends CliCommand<?>> {
    void initialize(T command);
}
