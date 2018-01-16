package com.kloudtek.ktcli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kloudtek.util.StringUtils;
import com.kloudtek.util.UserDisplayableException;
import com.kloudtek.util.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigTests {
    private File tmpConfig;

    public File createConfig() {
        try {
            if (tmpConfig != null) {
                throw new UserDisplayableException("Only one tmp config file supported");
            }
            File tmpDir = new File(".");
            File altTmpDir = new File(tmpDir, "build");
            if (altTmpDir.exists()) {
                tmpDir = altTmpDir;
            }
            tmpConfig = File.createTempFile("tmpconfig", ".json", tmpDir);
            if (tmpConfig.exists()) {
                if (!tmpConfig.delete()) {
                    throw new UserDisplayableException("Can't delete tmp file: " + tmpConfig.getPath());
                }
            }
            return tmpConfig;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File createConfig(String json) throws IOException {
        createConfig();
        IOUtils.write(tmpConfig, StringUtils.utf8(json));
        return tmpConfig;
    }

    @AfterEach
    public void deleteTmpConfig() {
        if (tmpConfig != null && tmpConfig.exists()) {
            if (!tmpConfig.delete()) {
                tmpConfig.deleteOnExit();
            }
        }
    }

    @Test
    public void testSubCommandsSaveConfig() throws IOException {
        createConfig();
        new CliHelper(ParentCmd::new).initAndRunNoExceptionHandling("-c=" + tmpConfig.getAbsolutePath(), "-sc", "-a=foo", "childcmd", "-b=bar");
        assertTrue(tmpConfig.exists());
        String json = IOUtils.toString(tmpConfig);
        Map jsonMap = CliHelper.getObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {
        });
        assertEquals(createTestConfigMap(), jsonMap);
    }

    @Test
    public void testSubCommandsLoadConfig() throws IOException {
        createConfig(CliHelper.getObjectMapper().writeValueAsString(createTestConfigMap()));
        CliHelper<ParentCmd> cliHelper = new CliHelper<>(ParentCmd::new);
        cliHelper.initAndRunNoExceptionHandling("-c=" + tmpConfig.getAbsolutePath(), "childcmd");
        ParentCmd parent = cliHelper.getCommand();
        assertEquals("foo", parent.a);
        ChildCmd childcmd = cliHelper.getCommandLine().getSubcommands().get("childcmd").getCommand();
        assertEquals("bar", childcmd.b);
    }

    @NotNull
    private HashMap<String, Object> createTestConfigMap() {
        HashMap<String, Object> top = new HashMap<>();
        top.put("defaultProfile", "default");
        HashMap<String, Object> profiles = new HashMap<>();
        top.put("profiles", profiles);
        HashMap<String, Object> parentProfile = new HashMap<>();
        profiles.put("default", parentProfile);
        parentProfile.put("a", "foo");
        HashMap<String, Object> subCommandsMap = new HashMap<>();
        parentProfile.put("subcommands", subCommandsMap);
        HashMap<String, Object> childProfile = new HashMap<>();
        childProfile.put("b", "bar");
        subCommandsMap.put("childcmd", childProfile);
        return top;
    }

    @CommandLine.Command(name = "parentcmd", subcommands = ChildCmd.class)
    public static class ParentCmd extends CliCommand<CliCommand> {
        @CommandLine.Option(names = "-a")
        @JsonProperty
        public String a;

        @Override
        protected void execute() throws Exception {
            System.out.println("executed parent");
        }
    }

    @CommandLine.Command(name = "childcmd")
    public static class ChildCmd extends CliCommand<ParentCmd> {
        @CommandLine.Option(names = "-b")
        @JsonProperty
        public String b;

        @Override
        protected void execute() throws Exception {
            System.out.println("executed child");
        }
    }
}
