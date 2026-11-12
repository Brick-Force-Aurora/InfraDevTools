package de.brickforceaurora.devtools.ui.instruction;

import java.nio.file.Path;

import de.brickforceaurora.devtools.Constant;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.lauriichan.snowframe.extension.Extension;
import me.lauriichan.snowframe.resource.source.IDataSource;

@Extension
public class DeleteInstructionEditor extends InstructionEditor {

    private static final String CMD_FORMAT = "delete \"%s\"";

    private final ObjectArrayList<String> deletions = new ObjectArrayList<>();
    private final ImString str = new ImString(Constant.FILE_PATH_LENGTH);

    public DeleteInstructionEditor() {
        super("Deletions", -1, true, true, true);
    }

    @Override
    public void reset() {
        deletions.clear();
    }
    
    public void legacyImport(String path) {
        if (deletions.contains(path)) {
            return;
        }
        deletions.add(path);
    }

    @Override
    protected void onAdd(Path absolutePath, Path relativePath, boolean directory) {
        if (relativePath == null) {
            return;
        }
        String path = relativePath.toString();
        if (deletions.contains(path)) {
            return;
        }
        deletions.add(path);
    }

    @Override
    protected void renderContent() {
        for (int i = 0; i < deletions.size(); i++) {
            String delItem = deletions.get(i);
            ImGui.pushID("d-" + delItem);
            try {
                if (ImGui.button("-")) {
                    deletions.remove(i--);
                    continue;
                }
            } finally {
                ImGui.popID();
            }
            ImGui.sameLine();
            str.set(delItem);
            ImGui.pushItemWidth(-1f);
            ImGui.inputText("##SrcD#" + delItem, str, ImGuiInputTextFlags.ReadOnly);
            ImGui.popItemWidth();
        }
    }

    @Override
    public boolean isExportable() {
        return true;
    }

    @Override
    public void writeUpdate(IDataSource tmpDir, ObjectArrayList<String> instructions) {
        deletions.stream().map(path -> CMD_FORMAT.formatted(formatPath(path))).forEach(instructions::add);
    }

}
