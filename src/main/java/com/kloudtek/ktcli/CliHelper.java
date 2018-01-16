package com.kloudtek.ktcli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kloudtek.ktcli.util.VerySimpleLogger;
import com.kloudtek.util.UnexpectedException;
import com.kloudtek.util.UserDisplayableException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.spi.LocationAwareLogger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class CliHelper<T extends CliCommand<?>> {
    public static final String SUBCOMMANDS = "subcommands";
    public static final String DEFAULT_PROFILE = "defaultProfile";
    public static final String DEFAULT = "default";
    public static final String PROFILES = "profiles";
    @Option(names = {"-q", "--quiet"}, description = "Suppress informative message")
    private boolean quiet;
    @Option(names = {"-v", "--verbose"}, description = "Verbose logging (overrides -q)")
    private boolean verbose;
    @Option(names = {"-sc", "--save-config"}, description = "Save all configurable parameters (marked with © symbol) into the specified profile")
    private boolean saveConfig;
    @Option(names = {"-p", "--profile"}, description = "Configuration profile")
    private String profile;
    @Option(names = {"-c", "--config"}, description = "Configuration File (note: this MUST be the first parameter, and be in the format of -c=<file> or --config=<file>)")
    private File configFile;
    protected ObjectNode config;
    private ObjectNode profileConfig;
    private static ObjectMapper objectMapper;
    private static Console console;
    private static Scanner scanner;
    private T command;
    private CommandLine commandLine;
    private CommandLine.Help.Ansi ansi = CommandLine.Help.Ansi.AUTO;
    private CommandCreator commandCreator;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.setDefaultPrettyPrinter(new DefaultPrettyPrinter());
        objectMapper.disable(MapperFeature.AUTO_DETECT_CREATORS,
                MapperFeature.AUTO_DETECT_FIELDS,
                MapperFeature.AUTO_DETECT_GETTERS,
                MapperFeature.AUTO_DETECT_IS_GETTERS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        console = System.console();
        if (console == null) {
            scanner = new Scanner(System.in);
        }
    }

    protected CliHelper() {
        if (console == null) {
            ansi = CommandLine.Help.Ansi.OFF;
        }
    }

    public CliHelper(CommandCreator commandCreator) {
        this();
        this.commandCreator = commandCreator;
    }

    // Configuration functions

    protected void loadConfigFile() {
        try {
            if (configFile.exists()) {
                JsonNode configNode = objectMapper.readTree(configFile);
                if (configNode.getNodeType() != JsonNodeType.OBJECT) {
                    throw new UserDisplayableException("Invalid configuration file " + configFile.getPath() + " is not a json object");
                }
                config = (ObjectNode) configNode;
                profile = getJsonString(config, DEFAULT_PROFILE, DEFAULT);
                ObjectNode profiles = getJsonObject(config, PROFILES);
                profileConfig = getJsonObject(profiles, profile);
            } else {
                config = new ObjectNode(JsonNodeFactory.instance);
                config.put(DEFAULT_PROFILE, DEFAULT);
                profile = DEFAULT;
                profileConfig = config.putObject(PROFILES).putObject(DEFAULT);
            }
        } catch (IOException e) {
            throw new UserDisplayableException("Unable to read configuration file: " + e.getMessage(), e);
        }
    }

    public void writeConfig() {
        if (saveConfig) {
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            } catch (IOException e) {
                System.out.println("Unable to write config file " + configFile.getPath() + " : " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Initialization and Execution

    /**
     * This will call {@link #parseBasicOptions(String[])}, {@link #loadConfigFile()},
     * {@link #parseAndExecute(String[])}, {@link #writeConfig()}. If an exception occurs, it will print it and call System.exit(-1).
     *
     * @param args Arguments
     */
    public void initAndRun(String... args) {
        try {
            initAndRunNoExceptionHandling(args);
        } catch (Exception e) {
            if (e instanceof CommandLine.ExecutionException) {
                System.out.println(((CommandLine.ExecutionException) e).getCommandLine().getCommandName() + " : " + e.getCause().getMessage());
            } else {
                System.out.println(e.getMessage());
            }
            if (isVerbose()) {
                e.printStackTrace();
            }
            System.exit(-1);
        }
    }

    /**
     * This will call {@link #parseBasicOptions(String[])}, {@link #loadConfigFile()},
     * {@link #parseAndExecute(String[])}, {@link #writeConfig()}.
     *
     * @param args Arguments
     */
    public void initAndRunNoExceptionHandling(String... args) {
        initAndRunNoExceptionHandling(null, args);
    }

    /**
     * This will call {@link #parseBasicOptions(String[])}, {@link #loadConfigFile()},
     * {@link #parseAndExecute(String[])}, {@link #writeConfig()}.
     *
     * @param args Arguments
     */
    public void initAndRunNoExceptionHandling(CommandClassInitializer<T> initializer, String... args) {
        parseBasicOptions(args);
        if (initializer != null) {
            initializer.initialize(command);
        }
        loadConfigFile();
        parseAndExecute(args);
        writeConfig();
    }

    public void parseAndExecute(String... args) throws CommandLine.ExecutionException {
        init(commandLine, profileConfig);
        commandLine.refreshDefaultValues();
        List<CommandLine> parsedCmdLines = commandLine.parse(args);
        if (CommandLine.printHelpIfRequested(parsedCmdLines, System.out, ansi)) {
            return;
        }
        CommandLine last = parsedCmdLines.get(parsedCmdLines.size() - 1);
        try {
            ((CliCommand) last.getCommand()).execute();
        } catch (Exception e) {
            throw new CommandLine.ExecutionException(last, "Error executing " + last.getCommandName() + ": " + e.getMessage(), e);
        }
        if (saveConfig) {
            for (CommandLine parsedCommand : parsedCmdLines) {
                Object cmd = parsedCommand.getCommand();
                if (cmd instanceof CliCommand<?>) {
                    ((CliCommand) cmd).saveConfig();
                }
            }
        }
    }

    public void setupLogging(CliHelper cliHelper) {
        if (cliHelper.verbose) {
            VerySimpleLogger.LOGLEVEL = LocationAwareLogger.DEBUG_INT;
        } else if (cliHelper.quiet) {
            VerySimpleLogger.LOGLEVEL = LocationAwareLogger.ERROR_INT;
        } else {
            VerySimpleLogger.LOGLEVEL = LocationAwareLogger.INFO_INT;
        }
    }

    @SuppressWarnings("unchecked")
    private void init(@NotNull CommandLine commandLine, @NotNull ObjectNode cfg) {
        try {
            CliCommand<?> cmd = commandLine.getCommand();
            loadExtraSubCommands(commandLine);
            cmd.loadConfig(cfg);
            cmd.init(this, commandLine, commandLine.getParent() != null ? commandLine.getParent().getCommand() : null);
            for (CommandLine subCmdLine : commandLine.getSubcommands().values()) {
                init(subCmdLine, getSubCommandConfigNode(cfg, subCmdLine.getCommandName()));
            }
        } catch (Exception e) {
            throw new UserDisplayableException("Error loading config: " + e.getMessage(), e);
        }
    }

    private void loadExtraSubCommands(@NotNull CommandLine commandLine) {
        CliCommand<?> cmd = commandLine.getCommand();
        List<CliCommand<?>> subModules = cmd.getExtraSubCommands();
        if (!subModules.isEmpty()) {
            for (CliCommand subCommand : subModules) {
                CommandLine subCmdLine = new CommandLine(subCommand);
                commandLine.addSubcommand(getCommandName(subCommand), subCmdLine);
                loadExtraSubCommands(subCmdLine);
            }
        }
    }

    private static String getCommandName(@NotNull CliCommand commandObj) {
        Command annotation = commandObj.getClass().getAnnotation(Command.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Subcommand " + commandObj.getClass().getName() + " isn't annotation with @Command");
        }
        return annotation.name();
    }

    // JSON Functions

    public static Map<String, Object> readJsonObject(String json) throws IOException {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };
        return getObjectMapper().readValue(json, typeRef);
    }

    public ObjectNode getJsonObject(ObjectNode parent, String name) {
        ObjectNode node;
        if (parent.has(name)) {
            try {
                node = (ObjectNode) parent.get(name);
            } catch (ClassCastException e) {
                throw new UserDisplayableException("Invalid configuration file " + configFile.getPath() + " '" + name + "' is not a json object");
            }
        } else {
            node = new ObjectNode(JsonNodeFactory.instance);
        }
        return node;
    }

    public String getJsonString(ObjectNode parent, String name, String defaultValue) {
        if (parent.has(name)) {
            return parent.get(name).textValue();
        } else {
            parent.put(name, defaultValue);
            return defaultValue;
        }
    }

    public static String readLine() {
        if (console != null) {
            return console.readLine();
        } else {
            return scanner.nextLine();
        }
    }

    public static String readPassword() {
        if (console != null) {
            return new String(console.readPassword());
        } else {
            return scanner.nextLine();
        }
    }

    public static boolean confirm(String txt) {
        return confirm(txt, null);
    }

    public static boolean confirm(String txt, Boolean defaultValue) {
        for (; ; ) {
            String defValStr = null;
            if (defaultValue != null && defaultValue) {
                defValStr = "yes";
            } else if (defaultValue != null && !defaultValue) {
                defValStr = "no";
            }
            String val = read(txt, defValStr);
            if (val != null) {
                val = val.trim().toLowerCase();
                switch (val) {
                    case "yes":
                    case "y":
                    case "true":
                        return true;
                    case "no":
                    case "n":
                    case "false":
                        return false;
                    default:
                        System.out.println("Response must be either: yes, no, n, y, true, false");
                }
            }
        }
    }

    public static String read(String txt, String defVal) {
        return read(txt, defVal, false);
    }

    public static String read(String txt, String defVal, boolean password) {
        for (; ; ) {
            System.out.print(txt);
            if (defVal != null) {
                System.out.print(" [" + (password ? "********" : defVal) + "]");
            }
            System.out.print(": ");
            System.out.flush();
            String val = password ? readPassword() : readLine();
            if (val != null) {
                val = val.trim();
                if (!val.isEmpty()) {
                    return val;
                }
                if (defVal != null) {
                    return defVal;
                }
            }
        }
    }

    /**
     * Parses basic options to get config file, and sets up logging based on appropriate flags
     *
     * @param args arguments
     */
    public CliHelper<T> parseBasicOptions(@NotNull String... args) {
        CliCommand<?> cmd = commandCreator.create();
        CommandLine cmdLine = new CommandLine(cmd);
        loadExtraSubCommands(cmdLine);
        cmdLine.setIgnoreRequired(true);
        CliHelper<T> cliHelper = new CliHelper<>();
        cmdLine.addMixin("cliHelper", cliHelper);
        cmdLine.parse(args);
        setupLogging(cliHelper);
        if (cliHelper.configFile != null) {
            configFile = cliHelper.configFile;
        } else {
            String commandName = cmdLine.getCommandName();
            if (commandName.equals("<main class>")) {
                throw new IllegalArgumentException("Command class " + command.getClass().getName() + " @Command and must have a name specified");
            }
            configFile = new File(System.getProperty("user.home") + File.separator + "." + commandName + ".cfg");
        }
        command = (T) commandCreator.create();
        commandLine = new CommandLine(command);
        commandLine.addMixin("cliHelper", this);
        return cliHelper;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public File getConfigFile() {
        return configFile;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    public boolean isSaveConfig() {
        return saveConfig;
    }

    public void setSaveConfig(boolean saveConfig) {
        this.saveConfig = saveConfig;
    }

    public ObjectNode getProfileConfig() {
        return profileConfig;
    }

    public T getCommand() {
        return command;
    }

    public CommandLine getCommandLine() {
        return commandLine;
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public <T extends CliCommand> void printUsage(CliCommand command) {
        command.getCommandLine().usage(System.out, ansi);
    }

    private static ObjectNode getSubCommandConfigNode(@NotNull ObjectNode cfg, String subCmdName) {
        ObjectNode subCommandsConfigNode = (ObjectNode) cfg.get(SUBCOMMANDS);
        if (subCommandsConfigNode == null) {
            subCommandsConfigNode = cfg.putObject(SUBCOMMANDS);
        }
        ObjectNode cfgNode = (ObjectNode) subCommandsConfigNode.get(subCmdName);
        if (cfgNode == null) {
            cfgNode = subCommandsConfigNode.putObject(subCmdName);
        }
        return cfgNode;
    }

    public interface CommandCreator {
        CliCommand<?> create();
    }

    public class ClassCommandCreator implements CommandCreator {
        private Class<? extends CliCommand<?>> cmdClass;

        public ClassCommandCreator(Class<? extends CliCommand<?>> cmdClass) {
            this.cmdClass = cmdClass;
        }

        @Override
        public CliCommand<?> create() {
            try {
                return cmdClass.newInstance();
            } catch (Exception e) {
                throw new UnexpectedException(e);
            }
        }
    }
}
