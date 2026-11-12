package de.brickforceaurora.devtools.ui.instruction;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.brickforceaurora.devtools.util.NFDUtil;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.lauriichan.snowframe.extension.ExtensionPoint;
import me.lauriichan.snowframe.extension.IExtension;
import me.lauriichan.snowframe.resource.source.IDataSource;

@ExtensionPoint
public abstract class InstructionEditor implements IExtension {

    private static final ThreadLocal<String> GAME_DIR = new ThreadLocal<>();
    private static final ThreadLocal<Path> GAME_DIR_PATH = new ThreadLocal<>();

    private final String title;
    private final int instructionsPriority;
    private final boolean allowFiles, allowDirectories, relativizePaths;

    public InstructionEditor(String title, int instructionsPriority) {
        this(title, instructionsPriority, false, false, true);
    }

    public InstructionEditor(String title, int instructionsPriority, boolean allowFiles, boolean allowDirectories,
        boolean relativizePaths) {
        this.title = title;
        this.instructionsPriority = instructionsPriority;
        this.allowFiles = allowFiles;
        this.allowDirectories = allowDirectories;
        this.relativizePaths = relativizePaths;
    }

    public final String title() {
        return title;
    }

    public final int instructionsPriority() {
        return instructionsPriority;
    }

    public abstract boolean isExportable();

    public final void render(String gameDir, boolean disabled) {
        String knownGameDir = GAME_DIR.get();
        if (knownGameDir != gameDir) {
            GAME_DIR.set(gameDir);
            GAME_DIR_PATH.set(Paths.get(gameDir));
        }
        if (ImGui.treeNodeEx(title, ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.DefaultOpen)) {
            renderContent();
            ImGui.beginDisabled(disabled);
            creationControls();
            ImGui.endDisabled();
            ImGui.treePop();
        }
    }

    protected void creationControls() {
        if (allowFiles) {
            if (ImGui.smallButton("+ File")) {
                Path gamePath = GAME_DIR_PATH.get();
                for (String pathString : NFDUtil.pickFiles(GAME_DIR.get())) {
                    Path path = Paths.get(pathString);
                    Path relPath = null;
                    if (relativizePaths) {
                        try {
                            relPath = gamePath.relativize(path);
                        } catch (RuntimeException _) {
                        }
                    }
                    onAdd(path, relPath, false);
                }
            }
        }
        if (allowDirectories) {
            if (allowFiles) {
                ImGui.sameLine();
            }
            if (ImGui.smallButton("+ Directory")) {
                Path gamePath = GAME_DIR_PATH.get();
                for (String pathString : NFDUtil.pickDirectories(GAME_DIR.get())) {
                    Path path = Paths.get(pathString);
                    Path relPath = null;
                    if (relativizePaths) {
                        try {
                            relPath = gamePath.relativize(path);
                        } catch (RuntimeException _) {
                        }
                    }
                    onAdd(path, relPath, true);
                }
            }
        }
    }

    protected final String formatPath(String path) {
        path = path.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    protected final Path relativizePath(Path path) {
        try {
            return GAME_DIR_PATH.get().relativize(path);
        } catch (RuntimeException exp) {
            return null;
        }
    }

    protected final String gameDir() {
        return GAME_DIR.get();
    }

    protected final Path gameDirPath() {
        return GAME_DIR_PATH.get();
    }

    public abstract void reset();

    protected void onAdd(Path absolutePath, Path relativePath, boolean directory) {}

    protected abstract void renderContent();

    public abstract void writeUpdate(IDataSource tmpDir, ObjectArrayList<String> instructions) throws IOException;

}
