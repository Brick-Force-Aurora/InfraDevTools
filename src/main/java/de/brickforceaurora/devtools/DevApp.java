package de.brickforceaurora.devtools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import de.brickforceaurora.devtools.command.HelpCommand;
import de.brickforceaurora.devtools.command.api.ConsoleActor;
import de.brickforceaurora.devtools.command.api.ICommand;
import de.brickforceaurora.devtools.ui.ImGuiStyler;
import de.brickforceaurora.devtools.ui.dock.DockNode;
import de.brickforceaurora.devtools.ui.dock.DockNode.SplitDirection;
import de.brickforceaurora.devtools.ui.dock.ImGuiDock;
import de.brickforceaurora.devtools.ui.widget.ImGuiConsole.LogHistory;
import de.brickforceaurora.devtools.util.ConsoleDelegateLogger;
import de.brickforceaurora.devtools.util.ExceptionCatchingExecutorService;
import de.brickforceaurora.devtools.util.console.Console;
import imgui.ImGui;
import imgui.flag.ImGuiConfigFlags;
import me.lauriichan.laylib.command.CommandManager;
import me.lauriichan.laylib.logger.ISimpleLogger;
import me.lauriichan.snowframe.ConfigModule;
import me.lauriichan.snowframe.ISnowFrameApp;
import me.lauriichan.snowframe.ImGUIModule;
import me.lauriichan.snowframe.SnowFrame;
import me.lauriichan.snowframe.lifecycle.Lifecycle;
import me.lauriichan.snowframe.lifecycle.LifecyclePhase.Stage;
import me.lauriichan.snowframe.resource.source.IDataSource;
import me.lauriichan.snowframe.util.logger.FileLogger;
import me.lauriichan.snowframe.util.logger.IDelegateLogger;

public final class DevApp implements ISnowFrameApp<DevApp> {

    public static final LogHistory LOG_HISTORY = new LogHistory();
    public static final ISimpleLogger LOGGER = new FileLogger(new File("../logs"),
        IDelegateLogger.combined(new ConsoleDelegateLogger(LOG_HISTORY), Console.INSTANCE));

    public static final ScheduledExecutorService SCHEDULER = new ExceptionCatchingExecutorService(
        Executors.newScheduledThreadPool(1, Thread.ofPlatform().daemon(true).name("Scheduler").factory()), LOGGER);

    private static SnowFrame<DevApp> snowFrame;
    private static ConsoleActor actor;

    static SnowFrame<DevApp> init(final String[] args) {
        if (snowFrame != null) {
            return snowFrame;
        }

        // TODO: Do actual command line parsing
        LOGGER.setDebug(Arrays.stream(args).anyMatch("--debug"::equalsIgnoreCase));

        snowFrame = SnowFrame.builder(new DevApp()).logger(LOGGER).build();
        actor = new ConsoleActor(snowFrame);
        return snowFrame;
    }

    public static DevApp get() {
        return snowFrame.app();
    }

    public static ConsoleActor actor() {
        return actor;
    }

    private CommandManager commandManager;

    private ImGuiDock mainDock;

    @Override
    public void registerLifecycle(Lifecycle<DevApp> lifecycle) {
        lifecycle.startupChain().register("load", Stage.PRE, frame -> {
            final File dir = new File("").getAbsoluteFile();
            Path appPath;
            if ("bin".equals(dir.getName())) {
                appPath = dir.getParentFile().toPath();
            } else {
                appPath = dir.toPath();
            }

            String userData = System.getenv("APPDATA");
            if (userData == null || userData.isBlank()) {
                userData = System.getenv("user.home") + File.separator + ".config";
            }
            userData += File.separator + "de.brickforceaurora.devtools";
            Path userPath = Paths.get(userData);
            Path tempPath = Files.createTempDirectory("de.brickforceaurora.devtools");

            frame.resourceManager().register("app", appPath);
            frame.resourceManager().register("data", appPath.resolve("data"));
            frame.resourceManager().register("tmp", tempPath);
            frame.resourceManager().register("user", userPath);
        });
        lifecycle.startupChain().register("ready", Stage.MAIN, frame -> {
            commandManager = new CommandManager();
            frame.extension(ICommand.class, false).callClasses(commandManager::register);
            HelpCommand.doHelp(actor, commandManager);
        });

        lifecycle.chainOrThrow(ImGUIModule.STARTUP_CHAIN).register("setup", Stage.POST, _ -> {
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
            ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
        }).register("init", Stage.POST, frame -> {
            frame.module(ImGUIModule.class)
                .setWindowIcon(frame.externalResource("jar://image/logo.png", "data://resources/image/logo.png"));
            FontAtlas.load(frame);
            ImGui.getIO().setIniFilename(null);
            ImGui.getIO().setFontDefault(FontAtlas.NOTO_SANS_NORMAL);
            ImGuiStyler.apply();
        }).register("start", Stage.PRE, frame -> {
            TextureAtlas.load(frame);
            {
                // Build main dock
                DockNode root = ImGuiDock.newDock().direction(SplitDirection.HORIZONTAL);

                root.newChild().id("sidebar").weight(0.2f);
                root.newChild().id("main").weight(0.8f);

                mainDock = root.build(frame, "main");
            }
        }).register("start", Stage.POST, frame -> {
            // We call Main.shutdown(), this will notify the GLFW to close.
            // However we already know it should close since this lambda is called.
            // So either the user requested to close, which means the application isn't aware yet, so we make it aware.
            // Or Main.shutdown() was called already in which case this does nothing.
            Main.shutdown();
            frame.lifecycle().execute(SnowFrame.LIFECYCLE_CHAIN_SHUTDOWN);
        });

        lifecycle.chainOrThrow(ImGUIModule.RENDER_CHAIN).register("render", Stage.MAIN, _ -> {
            mainDock.render();
        });

        lifecycle.shutdownChain().register("shutdown", Stage.POST, frame -> {
            if (frame.logger() instanceof final FileLogger logger) {
                logger.close();
            }
            frame.module(ConfigModule.class).manager().save();
            IDataSource tmpSrc = frame.resource("tmp://");
            if (tmpSrc.exists()) {
                tmpSrc.delete();
            }
        });
    }

    @Override
    public SnowFrame<DevApp> snowFrame() {
        return snowFrame;
    }

    public CommandManager commandManager() {
        return commandManager;
    }

}
