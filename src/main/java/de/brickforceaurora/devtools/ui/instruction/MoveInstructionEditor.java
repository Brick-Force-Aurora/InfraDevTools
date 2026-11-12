package de.brickforceaurora.devtools.ui.instruction;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.brickforceaurora.devtools.Constant;
import de.brickforceaurora.devtools.util.NFDUtil;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import me.lauriichan.snowframe.extension.Extension;
import me.lauriichan.snowframe.resource.source.IDataSource;

@Extension
public class MoveInstructionEditor extends InstructionEditor {

    private static final String CMD_FORMAT = "move \"%s\" \"%s\"";

    private static final NFDUtil.Filter[] EMPTY_FILTER = new NFDUtil.Filter[0];
    private static final ImVec2 NO_PADDING = new ImVec2(0, 0);

    private static record Move(String path, boolean dir) {}

    private final Object2ObjectLinkedOpenHashMap<String, Move> map = new Object2ObjectLinkedOpenHashMap<>();
    private final ImString str = new ImString(Constant.FILE_PATH_LENGTH);

    public MoveInstructionEditor() {
        super("Moves", 0, true, true, true);
    }

    @Override
    public void reset() {
        map.clear();
    }

    @Override
    protected void onAdd(Path absolutePath, Path relativePath, boolean directory) {
        if (relativePath == null) {
            return;
        }
        String path = relativePath.toString();
        if (map.containsKey(path)) {
            return;
        }
        map.put(path, new Move(null, directory));
    }

    @Override
    protected void renderContent() {
        ObjectBidirectionalIterator<Entry<String, Move>> iterator = map.object2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, Move> entry = iterator.next();
            ImGui.pushID("m-" + entry.getKey());
            try {
                if (ImGui.button("-")) {
                    iterator.remove();
                    continue;
                }
            } finally {
                ImGui.popID();
            }
            String targetPath = entry.getValue().path();
            ImGui.sameLine();
            ImGui.pushID("ms+" + entry.getKey());
            try {
                if (ImGui.button("Set")) {
                    if (entry.getValue().dir()) {
                        targetPath = NFDUtil.pickDirectory(gameDir());
                    } else {
                        String[] ext = entry.getKey().split("\\.");
                        NFDUtil.Filter[] filters = EMPTY_FILTER;
                        if (ext.length > 1) {
                            filters = new NFDUtil.Filter[] {
                                new NFDUtil.Filter(ext[ext.length - 1], ext[ext.length - 1])
                            };
                        }
                        targetPath = NFDUtil.pickFile(gameDir(), filters);
                    }
                    Path newPath = relativizePath(Paths.get(targetPath));
                    if (newPath == null) {
                        continue;
                    }
                    map.put(entry.getKey(), new Move(newPath.toString(), entry.getValue().dir()));
                }
            } finally {
                ImGui.popID();
            }
            if (targetPath == null) {
                targetPath = "";
            }
            ImGui.sameLine();
            ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, NO_PADDING);
            ImGui.beginTable("##Table#" + entry.getKey(), 3, ImGuiTableFlags.SizingStretchProp);
            ImGui.tableSetupColumn("1", ImGuiTableColumnFlags.NoHeaderLabel, 0.5f);
            ImGui.tableSetupColumn("2", ImGuiTableColumnFlags.NoHeaderLabel, 0.025f);
            ImGui.tableSetupColumn("3", ImGuiTableColumnFlags.NoHeaderLabel, 0.5f);
            ImGui.tableNextColumn();
            str.set(entry.getKey());
            ImGui.pushItemWidth(-1f);
            ImGui.inputText("##SrcM#" + entry.getKey(), str, ImGuiInputTextFlags.ReadOnly);
            ImGui.popItemWidth();
            ImGui.tableNextColumn();
            ImGui.text(" => ");
            ImGui.tableNextColumn();
            str.set(targetPath);
            ImGui.pushItemWidth(-1f);
            if (ImGui.inputText("##TargetM#" + entry.getKey(), str)) {
                if (!str.get().equals(targetPath)) {
                    map.put(entry.getKey(), new Move(str.get(), entry.getValue().dir()));
                }
            }
            ImGui.popItemWidth();
            ImGui.endTable();
            ImGui.popStyleVar();
        }
    }

    @Override
    public boolean isExportable() {
        return map.object2ObjectEntrySet().stream().allMatch(entry -> entry.getValue().path() != null);
    }

    @Override
    public void writeUpdate(IDataSource tmpDir, ObjectArrayList<String> instructions) throws IOException {
        for (Entry<String, Move> entry : map.object2ObjectEntrySet()) {
            instructions.add(CMD_FORMAT.formatted(formatPath(entry.getKey()), formatPath(entry.getValue().path())));
        }
    }

}
