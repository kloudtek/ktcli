package com.kloudtek.ktcli;

import com.kloudtek.util.StringUtils;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliHelperTests {
    public static final String SOMEVAL = "fsad8ofjsodafj";

    @Test
    public void testParseBasicOptions() {
        TestCmd cmd = new TestCmd();
        CliHelper cliHelper = new CliHelper(cmd);
        cliHelper.parseBasicOptions("-q", "doStuff");
        assertEquals(true, cliHelper.isQuiet());
    }

    @Test
    public void makeParameterNonRequired() throws IOException {
        TestCmdWithRequireField cmd = new TestCmdWithRequireField();
        cmd.someval = SOMEVAL;
        CliHelper cliHelper = new CliHelper(cmd);
        cliHelper.executeCommand();
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        cliHelper.getCommandLine().usage(new PrintStream(tmp));
        tmp.close();
        String usage = StringUtils.utf8(tmp.toByteArray());
        assertTrue(usage.contains(SOMEVAL));
        assertFalse(usage.contains("*"));
    }
}