package com.kloudtek.ktcli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliHelperTests {
    @Test
    public void testParseBasicOptions() {
        TestCmd cmd = new TestCmd();
        CliHelper cliHelper = new CliHelper(cmd);
        cliHelper.parseBasicOptions("-q", "doStuff");
        assertEquals(true, cliHelper.isQuiet());
    }
}