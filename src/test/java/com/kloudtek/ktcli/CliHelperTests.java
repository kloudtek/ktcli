package com.kloudtek.ktcli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kloudtek.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CliHelperTests {
    public static final String SOMEVAL = "fsad8ofjsodafj";
    public static final String MYPASSWORD = "mypassword";

    @Test
    public void testParseBasicOptions() {
        @CommandLine.Command(name = "testcmd", subcommands = DoStuffCmd.class)
        class TestCmd extends NonConfigurableCliCommand {
        }

        CliHelper<TestCmd> cliHelper = new CliHelper<TestCmd>(() -> new TestCmd()).parseBasicOptions("-v", "doStuff", "-co");
        assertEquals(true, cliHelper.isVerbose());
        assertNull(cliHelper.getConfigFile());
    }

    @Test
    public void makeParameterNonRequired() throws IOException {
        @CommandLine.Command(name = "testcmd", showDefaultValues = true, requiredOptionMarker = '*', notRequiredWithDefault = true)
        class TestCmdWithRequireField extends NonConfigurableCliCommand {
            @CommandLine.Option(names = "-val",required = true)
            String someval;
        }
        CliHelper<TestCmdWithRequireField> cliHelper = new CliHelper<>(() -> new TestCmdWithRequireField());
        cliHelper.initAndRunNoExceptionHandling(command -> command.someval = SOMEVAL);
        String usage = getUsage(cliHelper);
        assertTrue(usage.contains(SOMEVAL));
        assertFalse(usage.contains("*"));
    }

    @Test
    public void testDefaultPasswordHidden() throws IOException {
        CliHelper<TestCmdHideDefaultPassword> cliHelper = new CliHelper<>(TestCmdHideDefaultPassword::new);
        cliHelper.initAndRunNoExceptionHandling(command -> command.password = MYPASSWORD);
        String usage = getUsage(cliHelper);
        assertFalse(usage.contains(MYPASSWORD));
        assertTrue(usage.contains("*************"));
    }

    @Test
    public void testMultipleOptions() throws IOException {
        class App {
            @CommandLine.Option(names = "-v", arity = "2")
            List<Rak> val;
        }
        App app = new App();
        CommandLine commandLine = new CommandLine(app);
        commandLine.registerConverter(Rak.class, new CommandLine.ITypeConverter<Rak>() {
            @Override
            public Rak convert(String value) throws Exception {
                return null;
            }
        });
        commandLine.parse("-v", "foo", "bar", "-v", "bar");
        System.out.println();
    }

    public class Rak {
        @CommandLine.Parameters(index = "0")
        private String p1;
    }



    @CommandLine.Command(name = "testcmd", showDefaultValues = true, notRequiredWithDefault = true)
    public static class TestCmdHideDefaultPassword extends NonConfigurableCliCommand {
        @CommandLine.Option(names = "-pw",description = "a password", defaultValueMask = "*************")
        public String password;
    }

    @CommandLine.Command(name = "doStuff")
    static class DoStuffCmd extends NonConfigurableCliCommand {
        @CommandLine.Option(names = "-co")
        boolean co;
    }

    @NotNull
    private String getUsage(CliHelper<?> cliHelper) throws IOException {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        cliHelper.getCommandLine().usage(new PrintStream(tmp));
        tmp.close();
        return StringUtils.utf8(tmp.toByteArray());
    }

    public static class NonConfigurableCliCommand extends CliCommand<CliCommand<?>> {
        @Override
        protected void loadConfig(@NotNull ObjectNode cfg) throws Exception {
        }

        @Override
        protected void saveConfig() {
        }
    }
}