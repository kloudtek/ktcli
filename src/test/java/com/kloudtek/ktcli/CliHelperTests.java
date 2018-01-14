package com.kloudtek.ktcli;

import com.kloudtek.util.StringUtils;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class CliHelperTests {
    public static final String SOMEVAL = "fsad8ofjsodafj";
    public static final String MYPASSWORD = "mypassword";

    @Test
    public void testParseBasicOptions() {
        @CommandLine.Command(name = "testcmd")
        class TestCmd extends CliCommand<CliCommand> {
        }
        TestCmd cmd = new TestCmd();
        CliHelper cliHelper = new CliHelper(cmd);
        cliHelper.parseBasicOptions("-q", "doStuff");
        assertEquals(true, cliHelper.isQuiet());
    }

    @Test
    public void makeParameterNonRequired() throws IOException {
        @CommandLine.Command(name = "testcmd", showDefaultValues = true,requiredOptionMarker = '*')
        class TestCmdWithRequireField extends CliCommand<CliCommand> {
            @CommandLine.Option(names = "-val",required = true)
            String someval;
        }
        TestCmdWithRequireField cmd = new TestCmdWithRequireField();
        cmd.someval = SOMEVAL;
        CliHelper cliHelper = new CliHelper(cmd);
        cliHelper.initAndRunNoExceptionHandling();
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        cliHelper.getCommandLine().usage(new PrintStream(tmp));
        tmp.close();
        String usage = StringUtils.utf8(tmp.toByteArray());
        assertTrue(usage.contains(SOMEVAL));
        assertFalse(usage.contains("*"));
    }

    @Test
    public void testDefaultPasswordHidden() throws IOException {
        TestCmdHideDefaultPassword cmd = new TestCmdHideDefaultPassword();
        CliHelper cliHelper = new CliHelper(cmd);
        cliHelper.parseBasicOptions();
        cmd.password = MYPASSWORD;
        cliHelper.loadConfigFile();
        cliHelper.setupLogging(cliHelper);
        cliHelper.executeCommand();
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        cliHelper.getCommandLine().usage(new PrintStream(tmp));
        tmp.close();
        String usage = StringUtils.utf8(tmp.toByteArray());
        assertFalse(usage.contains(MYPASSWORD));
        assertTrue(usage.contains("*************"));
    }

    @CommandLine.Command(name = "testcmd", showDefaultValues = true, notRequiredWithDefault = true)
    public static class TestCmdHideDefaultPassword extends CliCommand<CliCommand> {
        @CommandLine.Option(names = "-pw",description = "a password", defaultValueMask = "*************")
        public String password;
    }
}