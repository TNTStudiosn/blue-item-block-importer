package com.tntstudios.blueimporter.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeGenerator {
    private static final Logger LOG = Logger.getInstance(CodeGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Punto único de entrada para generar TODO lo necesario de un modelo.
     */
    public static void generate(Project project,
                                String modId,
                                String tipo,
                                String modelName,
                                String displayName,
                                Map<String, String> textures) {
        LOG.info("=== Generando modelo: " + modelName + " (" + tipo + ") ===");

        createLangEntry(project, modId, modelName, displayName);
        createLootTable(project, modId, modelName);
        createBlockstate(project, modId, modelName);
        createBlockClass(project, modId, modelName);
        updateRegistry(project, modId, modelName);

        LOG.info("Modelo " + modelName + " generado correctamente.");
    }

    private static void createLangEntry(Project project,
                                        String modId,
                                        String modelName,
                                        String displayName) {
        LOG.info("  • Actualizando lang: " + modelName);
        Path langDir = Paths.get(project.getBasePath(),
                "src/main/resources/assets",
                modId, "lang");
        try {
            Files.createDirectories(langDir);
            Path langFile = langDir.resolve("en_us.json");

            JsonObject root = Files.exists(langFile)
                    ? GSON.fromJson(Files.readString(langFile), JsonObject.class)
                    : new JsonObject();

            String key = "block." + modId + "." + modelName;
            root.addProperty(key, displayName);

            Files.writeString(langFile,
                    GSON.toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e) {
            LOG.error("Error al escribir lang: " + e.getMessage(), e);
            Messages.showErrorDialog("Error al actualizar lang: " + e.getMessage(), "Error");
        }
    }

    private static void createLootTable(Project project,
                                        String modId,
                                        String modelName) {
        LOG.info("  • Creando loot table: " + modelName);
        try {
            Path dir = Paths.get(project.getBasePath(),
                    "src/main/resources/data",
                    modId, "loot_tables/blocks");
            Files.createDirectories(dir);
            Path file = dir.resolve(modelName + ".json");

            JsonObject root = new JsonObject();
            root.addProperty("type", "minecraft:block");
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);

            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", modId + ":" + modelName);
            pool.add("entries", GSON.toJsonTree(new JsonObject[]{entry}));

            JsonObject cond = new JsonObject();
            cond.addProperty("condition", "minecraft:survives_explosion");
            pool.add("conditions", GSON.toJsonTree(new JsonObject[]{cond}));

            root.add("pools", GSON.toJsonTree(new JsonObject[]{pool}));
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            LOG.error("Error creando loot table: " + e.getMessage(), e);
            Messages.showErrorDialog("Error creando loot table: " + e.getMessage(), "Error");
        }
    }

    private static void createBlockstate(Project project,
                                         String modId,
                                         String modelName) {
        LOG.info("  • Creando blockstate: " + modelName);
        try {
            Path dir = Paths.get(project.getBasePath(),
                    "src/main/resources/assets",
                    modId, "blockstates");
            Files.createDirectories(dir);
            Path file = dir.resolve(modelName + ".json");

            JsonObject variants = new JsonObject();
            for (var e : Map.of(
                    "north", 180,
                    "south", 0,
                    "west", 90,
                    "east", 270
            ).entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("model", modId + ":block/" + modelName);
                if (!"south".equals(e.getKey())) {
                    v.addProperty("y", e.getValue());
                }
                variants.add("facing=" + e.getKey(), v);
            }
            JsonObject root = new JsonObject();
            root.add("variants", variants);

            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            LOG.error("Error creando blockstate: " + e.getMessage(), e);
            Messages.showErrorDialog("Error creando blockstate: " + e.getMessage(), "Error");
        }
    }

    private static void createBlockClass(Project project,
                                         String modId,
                                         String modelName) {
        LOG.info("  • Generando clase Java: " + modelName + "Block");
        String className = capitalize(modelName) + "Block";
        Path srcDir = Paths.get(project.getBasePath(),
                "src/main/java",
                "com", "tntstudios", modId, "blocks");
        try {
            Files.createDirectories(srcDir);
            Path file = srcDir.resolve(className + ".java");

            if (Files.exists(file)) {
                LOG.warn("    – Ya existe " + file + ", no se sobrescribe.");
                return;
            }

            String template =
                    "package com.tntstudios." + modId + ".blocks;\n\n" +
                            "import com.tntstudios." + modId + ".util.VoxelShapeUtil;\n" +
                            "import net.minecraft.block.BlockState;\n" +
                            "import net.minecraft.block.ShapeContext;\n" +
                            "import net.minecraft.state.property.Properties;\n" +
                            "import net.minecraft.util.math.BlockPos;\n" +
                            "import net.minecraft.util.math.Direction;\n" +
                            "import net.minecraft.util.shape.VoxelShape;\n" +
                            "import net.minecraft.util.shape.VoxelShapes;\n" +
                            "import net.minecraft.world.BlockView;\n\n" +
                            "import java.util.EnumMap;\n\n" +
                            "public class " + className + " extends FacingXBlock {\n\n" +
                            "    private static final VoxelShape BASE_SHAPE = VoxelShapes.union(\n" +
                            "        // TODO: añade aquí tus cuboides\n" +
                            "    );\n\n" +
                            "    private static final EnumMap<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);\n\n" +
                            "    static {\n" +
                            "        SHAPES.put(Direction.NORTH, BASE_SHAPE);\n" +
                            "        SHAPES.put(Direction.SOUTH, VoxelShapeUtil.rotateY(BASE_SHAPE, 180));\n" +
                            "        SHAPES.put(Direction.WEST, VoxelShapeUtil.rotateY(BASE_SHAPE, 90));\n" +
                            "        SHAPES.put(Direction.EAST, VoxelShapeUtil.rotateY(BASE_SHAPE, 270));\n" +
                            "    }\n\n" +
                            "    public " + className + "(Settings settings) {\n" +
                            "        super(settings);\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {\n" +
                            "        return SHAPES.getOrDefault(state.get(Properties.HORIZONTAL_FACING), BASE_SHAPE);\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {\n" +
                            "        return SHAPES.getOrDefault(state.get(Properties.HORIZONTAL_FACING), BASE_SHAPE);\n" +
                            "    }\n" +
                            "}\n";

            Files.writeString(file,
                    template,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e) {
            LOG.error("Error creando clase Java: " + e.getMessage(), e);
            Messages.showErrorDialog("Error creando clase Java: " + e.getMessage(), "Error");
        }
    }

    private static void updateRegistry(Project project,
                                       String modId,
                                       String modelName) {
        LOG.info("  • Actualizando registro en BlocksRegistry");
        Path registryFile = Paths.get(project.getBasePath(),
                "src/main/java",
                "com", "tntstudios", modId, "registry",
                "BlocksRegistry.java");
        if (!Files.exists(registryFile)) {
            LOG.warn("    – No existe " + registryFile + ", omitiendo registro.");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(registryFile, StandardCharsets.UTF_8);
            String constant = modelName.toUpperCase() + "_BLOCK";
            boolean already = lines.stream()
                    .anyMatch(l -> l.contains(constant + " = register(\"" + modelName + "\""));
            if (already) {
                LOG.info("    – Ya estaba registrado, no se duplica.");
                return;
            }

            String className = capitalize(modelName) + "Block";
            String declaration =
                    "    public static final Block " + constant + " = register(\"" + modelName + "\",\n" +
                            "        new " + className + "(AbstractBlock.Settings.create().strength(0.2f).nonOpaque())\n" +
                            "    );";

            // insertar justo antes de registerAll()
            int insertAt = lines.indexOf(
                    lines.stream()
                            .filter(l -> l.trim().startsWith("public static void registerAll"))
                            .findFirst()
                            .orElse("")
            );
            if (insertAt < 0) insertAt = lines.size() - 1;

            lines.add(insertAt, declaration);
            Files.write(registryFile,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);

            LOG.info("    – Registro insertado correctamente.");
        }
        catch (IOException e) {
            LOG.error("Error al actualizar registry: " + e.getMessage(), e);
            Messages.showErrorDialog("Error al actualizar registry: " + e.getMessage(), "Error");
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
