package com.kloudtek.ktcli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kloudtek.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import com.kloudtek.ktcli.util.VerySimpleLogger;
import com.kloudtek.util.UserDisplayableException;
import org.slf4j.spi.LocationAwareLogger;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class CliHelper {
    public static final String SUBCOMMANDS = "subcommands";
    public static final String DEFAULT_PROFILE = "defaultProfile";
    public static final String DEFAULT = "default";
    public static final String PROFILES = "profiles";
    @Option(names = {"-h", "--help"}, description = "Display command usage", usageHelp = true)
    private boolean help;
    @Option(names = {"-q", "--quiet"}, description = "Suppress informative message")
    private boolean quiet;
    @Option(names = {"-v", "--verbose"}, description = "Verbose logging (overrides -q)")
    private boolean verbose;
    @Option(names = {"-sc", "--save-config"}, description = "Save all configurable parameters (marked with © symbol) into the specified profile")
    private boolean saveConfig;
    @Option(names = {"-p", "--profile"}, description = "Configuration profile")
    private String profile;
    @Option(names = {"-c", "--config"}, description = "Configuration File")
    private File configFile;
    protected ObjectNode config;
    private ObjectNode profileConfig;
    private static ObjectMapper objectMapper;
    private static Console console;
    private static Scanner scanner;
    private CliCommand<?> command;
    private final CommandLine commandLine;
    private CommandLine.Help.Ansi ansi = CommandLine.Help.Ansi.AUTO;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.setDefaultPrettyPrinter(new DefaultPrettyPrinter());
        objectMapper.disable(MapperFeature.AUTO_DETECT_CREATORS,
                MapperFeature.AUTO_DETECT_FIELDS,
                MapperFeature.AUTO_DETECT_GETTERS,
                MapperFeature.AUTO_DETECT_IS_GETTERS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        console = System.console();
        if (console == null) {
            scanner = new Scanner(System.in);
        }
    }

    public CliHelper(CliCommand<?> command) {
        this.command = command;
        commandLine = new CommandLine(command);
        if( commandLine.getCommandName().equals("<main class>") ) {
            throw new IllegalArgumentException("Command class "+command.getClass().getName()+" must be annotated with @Command and must have a name specified");
        }
        commandLine.addMixin("cliHelper", this);
        configFile = new File(System.getProperty("user.home") + File.separator + "."+commandLine.getCommandName());
        commandLine.getCommandSpec().optionsMap().get("-c").defaultValue(configFile);
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
            commandLine.getCommandSpec().optionsMap().get("-p").defaultValue(profile);
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
     * This will call {@link #parseBasicOptions(String[])}, {@link #loadConfigFile()}, {@link #setupLogging()},
     * {@link #executeCommand(String[])}, {@link #writeConfig()}. If an exception occurs, it will print it and call System.exit(-1).
     *
     * @param args Arguments
     */
    public void initAndRun(String... args) {
        try {
            parseBasicOptions(args);
            loadConfigFile();
            setupLogging();
            executeCommand(args);
            writeConfig();
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

    public void executeCommand(String... args) throws CommandLine.ExecutionException {
        init(commandLine, null, profileConfig);
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


    public void setupLogging() {
        if (verbose) {
            VerySimpleLogger.LOGLEVEL = LocationAwareLogger.DEBUG_INT;
        } else if (quiet) {
            VerySimpleLogger.LOGLEVEL = LocationAwareLogger.ERROR_INT;
        } else {
            VerySimpleLogger.LOGLEVEL = LocationAwareLogger.INFO_INT;
        }
    }

    @SuppressWarnings("unchecked")
    private void init(@NotNull CommandLine commandLine, @Nullable CliCommand parent, @Nullable ObjectNode cfg) {
        try {
            Object commandObj = commandLine.getCommand();
            if (commandObj instanceof CliCommand) {
                CliCommand cliCmd = (CliCommand) commandObj;
                cliCmd.init(this, commandLine, parent, cfg);
                cliCmd.loadConfig();
                List<CliCommand> subModules = cliCmd.getExtraSubCommands();
                if (!subModules.isEmpty()) {
                    for (CliCommand subCommand : subModules) {
                        CommandLine.Command annotation = subCommand.getClass().getAnnotation(CommandLine.Command.class);
                        if (annotation == null) {
                            throw new UserDisplayableException("Command is missing annotation: " + subCommand.getClass().getName());
                        }
                        CommandLine subCmdLine = new CommandLine(subCommand);
                        String subCmdName = annotation.name();
                        commandLine.addSubcommand(subCmdName, subCmdLine);
                    }
                }
                List<CommandLine.ArgSpec> reqArgs = cliCmd.commandLine.getCommandSpec().requiredArgs();
                for (CommandLine.ArgSpec argSpec : new ArrayList<>(reqArgs)) {
                    if( argSpec.getter().get() != null ) {
                        reqArgs.remove(argSpec);
                        argSpec.required(false);
                    }
                }
                for (Map.Entry<String, CommandLine> subCmdEntry : commandLine.getSubcommands().entrySet()) {
                    ObjectNode subCommandsConfigNode = (ObjectNode) cfg.get(SUBCOMMANDS);
                    if (subCommandsConfigNode == null) {
                        subCommandsConfigNode = cfg.putObject(SUBCOMMANDS);
                    }
                    CommandLine subCmdLine = subCmdEntry.getValue();
                    ObjectNode subCmdConfigNode = (ObjectNode) subCommandsConfigNode.get(subCmdLine.getCommandName());
                    if (subCmdConfigNode == null) {
                        subCmdConfigNode = subCommandsConfigNode.putObject(subCmdLine.getCommandName());
                    }
                    init(subCmdLine, cliCmd, subCmdConfigNode);
                }
            }
        } catch (Exception e) {
            throw new UserDisplayableException("Error loading config: " + e.getMessage(), e);
        }
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

    public void parseBasicOptions(@NotNull String... args) {
        try {
            commandLine.parse(args);
        } catch (Exception e) {
            // that's fine, we only want the basic options
        }
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

    public CliCommand<?> getCommand() {
        return command;
    }

    public CommandLine getCommandLine() {
        return commandLine;
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
