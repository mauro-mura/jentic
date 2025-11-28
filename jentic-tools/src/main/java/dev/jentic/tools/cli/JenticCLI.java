package dev.jentic.tools.cli;

import dev.jentic.tools.cli.commands.ConfigCommand;
import dev.jentic.tools.cli.commands.HealthCommand;
import dev.jentic.tools.cli.commands.ListCommand;
import dev.jentic.tools.cli.commands.LogsCommand;
import dev.jentic.tools.cli.commands.StartCommand;
import dev.jentic.tools.cli.commands.StatusCommand;
import dev.jentic.tools.cli.commands.StopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI entry point for Jentic framework.
 *
 * <p>Usage: {@code jentic [COMMAND] [OPTIONS]}
 */
@Command(
    name = "jentic",
    description = "Jentic Multi-Agent Framework CLI",
    version = "0.4.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    subcommands = {
        ListCommand.class,
        StatusCommand.class,
        StartCommand.class,
        StopCommand.class,
        LogsCommand.class,
        ConfigCommand.class,
        HealthCommand.class
    }
)
public class JenticCLI implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JenticCLI())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
