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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeGenerator {
    private static final Logger LOG = Logger.getInstance(CodeGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Punto único de entrada para generar TODO lo necesario de un modelo.
     */
    public static void generate(Project project,
                                String modId,
                                String tipo,
                                String modelName,
                                String displayName,
                                Map<String, String> textures,
                                boolean useGecko,
                                String version) {
        LOG.info("=== Generando modelo: " + modelName + " (" + tipo + ") ===");

        createLangEntry(project, modId, tipo, modelName, displayName);

        if ("block".equals(tipo)) {
            createLootTable(project, modId, modelName);
            createBlockstate(project, modId, modelName);
            createBlockClass(project, modId, modelName);
            updateBlocksRegistry(project, modId, modelName);
        } else if ("item".equals(tipo)) {
            createItemModel(project, modId, modelName);
            updateItemsRegistry(project, modId, modelName);
        }

        ensureVoxelShapeUtil(project, modId);
        updateTabsRegistry(project, modId, modelName);
        createClientInitializer(project, modId);

        LOG.info("Modelo " + modelName + " generado correctamente.");
    }

    private static void createLangEntry(Project project,
                                        String modId,
                                        String tipo,
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

            String key = ("block".equals(tipo) ? "block." : "item.") + modId + "." + modelName;
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
            var pool = new JsonObject();
            pool.addProperty("rolls", 1);

            var entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", modId + ":" + modelName);
            pool.add("entries", GSON.toJsonTree(new JsonObject[]{entry}));

            var cond = new JsonObject();
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
                var v = new JsonObject();
                v.addProperty("model", modId + ":block/" + modelName);
                if (!"south".equals(e.getKey())) {
                    v.addProperty("y", e.getValue());
                }
                variants.add("facing=" + e.getKey(), v);
            }
            var root = new JsonObject();
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
                    "package com.TNTStudios." + modId + ".blocks;\n" +
                            "\n" +
                            "import com.TNTStudios." + modId + ".util.VoxelShapeUtil;\n" +
                            "import net.minecraft.block.BlockState;\n" +
                            "import net.minecraft.block.ShapeContext;\n" +
                            "import net.minecraft.state.property.Properties;\n" +
                            "import net.minecraft.util.math.BlockPos;\n" +
                            "import net.minecraft.util.math.Direction;\n" +
                            "import net.minecraft.util.shape.VoxelShape;\n" +
                            "import net.minecraft.util.shape.VoxelShapes;\n" +
                            "import net.minecraft.world.BlockView;\n" +
                            "\n" +
                            "import java.util.EnumMap;\n" +
                            "\n" +
                            "public class " + className + " extends FacingXBlock {\n" +
                            "\n" +
                            "    private static final VoxelShape BASE_SHAPE = VoxelShapes.union(\n" +
                            "        // TODO: añade aquí tus cuboides\n" +
                            "    );\n" +
                            "\n" +
                            "    private static final EnumMap<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);\n" +
                            "\n" +
                            "    static {\n" +
                            "        SHAPес.put( Direction.NORTH, BASE_SHAPE);\n" +
                            "        SHAPес.put( Direction.SOUTH, VoxelShapeUtil.rotateY(BASE_SHAPE, 180));\n" +
                            "        SHAPيس.put(Direction.WEST, VoxelShapeUtil.rotateY(BASE_SHAPE, 90));\n" +
                            "        SHAPύ.put(Direction.EAST, VoxelShapeUtil.rotateY(BASE_SHAPE, 270));\n" +
                            "    }\n" +
                            "\n" +
                            "    public " + className + "(Settings settings) {\n" +
                            "        super(settings);\n" +
                            "    }\n" +
                            "\n" +
                            "    @Override\n" +
                            "    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {\n" +
                            "        return SHAPناس.getOrDefault(state.get(Properties.HORIZONTAL_FACING), BASE_SHAPE);\n" +
                            "    }\n" +
                            "\n" +
                            "    @Override\n" +
                            "    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {\n" +
                            "        return SHAP而.getOrDefault(state.get(Properties.HORIZONTAL_FACING), BASE_SHAPE);\n" +
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

    private static void createItemModel(Project project,
                                        String modId,
                                        String modelName) {
        LOG.info("  • Creando modelo de item: " + modelName);
        try {
            Path dir = Paths.get(project.getBasePath(),
                    "src/main/resources/assets",
                    modId, "models/item");
            Files.createDirectories(dir);
            Path file = dir.resolve(modelName + ".json");
            JsonObject root = new JsonObject();
            root.addProperty("parent", modId + ":block/" + modelName);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Error creando modelo de item: " + e.getMessage(), e);
            Messages.showErrorDialog("Error creando modelo de item: " + e.getMessage(), "Error");
        }
    }

    private static void updateItemsRegistry(Project project,
                                            String modId,
                                            String modelName) {
        LOG.info("  • Actualizando ItemsRegistry: " + modelName);
        Path registryFile = Paths.get(project.getBasePath(),
                "src/main/java",
                "com", "tntstudios", modId, "registry",
                "ItemsRegistry.java");
        if (!Files.exists(registryFile)) {
            // Crear fichero básico
            String className = capitalize(modId) + "Items";
            String content =
                    "package com.TNTStudios." + modId + ".registry;\n\n" +
                            "import net.minecraft.item.Item;\n" +
                            "import net.minecraft.item.ItemGroup;\n" +
                            "import net.minecraft.item.ItemStack;\n" +
                            "import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;\n" +
                            "import net.minecraft.registry.Registry;\n" +
                            "import net.minecraft.registry.Registries;\n" +
                            "import net.minecraft.util.Identifier;\n\n" +
                            "public class ItemsRegistry {\n" +
                            "    public static void registerAll() {\n" +
                            "        // Registrations here\n" +
                            "    }\n" +
                            "}\n";
            try {
                Files.createDirectories(registryFile.getParent());
                Files.writeString(registryFile, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.error("Error creando ItemsRegistry: " + e.getMessage(), e);
            }
            return;
        }
        try {
            List<String> lines = Files.readAllLines(registryFile, StandardCharsets.UTF_8);
            String constName = modelName.toUpperCase() + "_ITEM";
            String declaration =
                    "    public static final Item " + constName + " = Registry.register(Registries.ITEM, " +
                            "new Identifier(\"" + modId + "\", \"" + modelName + "\"), new Item(new Item.Settings()));";
            if (lines.stream().noneMatch(l -> l.contains(constName))) {
                int idx = lines.indexOf(lines.stream()
                        .filter(l -> l.contains("registerAll"))
                        .findFirst().orElse(""));
                lines.add(idx, declaration);
                Files.write(registryFile, lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.error("Error actualizando ItemsRegistry: " + e.getMessage(), e);
        }
    }

    private static void ensureVoxelShapeUtil(Project project,
                                             String modId) {
        LOG.info("  • Asegurando VoxelShapeUtil");
        Path file = Paths.get(project.getBasePath(),
                "src/main/java",
                "com", "tntstudios", modId, "util",
                "VoxelShapeUtil.java");
        if (Files.exists(file)) return;
        // Crear clase VoxelShapeUtil
        String content =
                "package com.TNTStudios." + modId + ".util;\n\n" +
                        "import net.minecraft.util.shape.VoxelShape;\n" +
                        "import net.minecraft.util.shape.VoxelShapes;\n" +
                        "import net.minecraft.util.math.Box;\n\n" +
                        "public class VoxelShapeUtil {\n\n" +
                        "    public static VoxelShape rotateY(VoxelShape shape, int degrees) {\n" +
                        "        int times = (degrees / 90) % 4;\n" +
                        "        VoxelShape rotatedShape = shape;\n" +
                        "        for (int i = 0; i < times; i++) {\n" +
                        "            VoxelShape[] buffer = new VoxelShape[]{VoxelShapes.empty()};\n" +
                        "            rotatedShape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {\n" +
                        "                buffer[0] = VoxelShapes.union(buffer[0], VoxelShapes.cuboid(\n" +
                        "                        minZ, minY, 1 - maxX,\n" +
                        "                        maxZ, maxY, 1 - minX\n" +
                        "                ));\n" +
                        "            });\n" +
                        "            rotatedShape = buffer[0];\n" +
                        "        }\n" +
                        "        return rotatedShape;\n" +
                        "    }\n" +
                        "}";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Error creando VoxelShapeUtil: " + e.getMessage(), e);
        }
    }

    private static void updateTabsRegistry(Project project, String modId, String modelName) {
        LOG.info("  • Actualizando pestaña creativa");
        Path file = Paths.get(project.getBasePath(),
                "src/main/java",
                "com", "tntstudios", modId, "registry",
                capitalize(modId) + "Tabs.java");
        String className = capitalize(modId) + "Tabs";
        String idLower = modId.toLowerCase();
        if (Files.exists(file)) {
            // TODO: actualizar entradas si es necesario
            return;
        }
        String content =
                "package com.TNTStudios." + modId + ".registry;\n\n" +
                        "import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;\n" +
                        "import net.minecraft.item.ItemGroup;\n" +
                        "import net.minecraft.item.ItemStack;\n" +
                        "import net.minecraft.text.Text;\n" +
                        "import net.minecraft.util.Identifier;\n" +
                        "import net.minecraft.registry.Registry;\n" +
                        "import net.minecraft.registry.Registries;\n" +
                        "import net.minecraft.registry.RegistryKey;\n\n" +
                        "public class " + className + " {\n" +
                        "    public static final RegistryKey<ItemGroup> " + idLower.toUpperCase() + "_TAB = RegistryKey.of(Registries.ITEM_GROUP.getKey(), new Identifier(\"" + idLower + "\", \"main\"));\n" +
                        "    public static void register() {\n" +
                        "        Registry.register(Registries.ITEM_GROUP, " + idLower.toUpperCase() + "_TAB,\n" +
                        "            FabricItemGroup.builder()\n" +
                        "                .displayName(Text.literal(\"" + capitalize(modId) + "\"))\n" +
                        "                .icon(() -> new ItemStack(ItemsRegistry." + modelName.toUpperCase() + "_ITEM))\n" +
                        "                .entries((context, entries) -> {\n" +
                        "                    // Añade tus ítems aquí\n" +
                        "                })\n" +
                        "                .build()\n" +
                        "        );\n" +
                        "    }\n" +
                        "}";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Error creando TabsRegistry: " + e.getMessage(), e);
        }
    }

    private static void createClientInitializer(Project project, String modId) {
        LOG.info("  • Creando inicializador cliente");
        Path clientDir = Paths.get(project.getBasePath(),
                "src", "client", "java",
                "com", "TNTStudios", modId, "client");
        try {
            Files.createDirectories(clientDir);

            // 1) Clase para registro de texturas Cutout
            Path cutoutClass = clientDir.resolve("CutoutRegistrar.java");
            if (!Files.exists(cutoutClass)) {
                String cutoutContent =
                        "package com.TNTStudios." + modId + ".client;\n\n" +
                                "import net.fabricmc.api.ClientModInitializer;\n" +
                                "import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;\n" +
                                "import net.minecraft.client.render.RenderLayer;\n" +
                                "import com.TNTStudios." + modId + ".registry.BlocksRegistry;\n\n" +
                                "/**\n" +
                                " * Clase generada para registrar texturas cutout.\n" +
                                " * // en tu clase principal de cliente pon:\n" +
                                " * //     CutoutRegistrar.registerClient();\n" +
                                " *\n" +
                                " * // dentro de esa misma clase para registrar en la clase principal de main todos los códigos que creamos:\n" +
                                " * //     com.TNTStudios." + modId + ".registry.BlocksRegistry.registerAll();\n" +
                                " */\n" +
                                "public class CutoutRegistrar implements ClientModInitializer {\n" +
                                "    @Override\n" +
                                "    public void onInitializeClient() {\n" +
                                "        // Registrando capas de render\n" +
                                "        BlockRenderLayerMap.INSTANCE.putBlock(BlocksRegistry.HAPPYMEAL_BLOCK, RenderLayer.getCutout());\n" +
                                "        // Añade tus bloques aquí...\n" +
                                "    }\n" +
                                "}\n";
                Files.writeString(cutoutClass, cutoutContent, StandardCharsets.UTF_8);
            }

            // 2) Comentarios para el usuario:
            // en tu clase principal de cliente pon:
            //     CutoutRegistrar.registerClient();
            //
            // dentro de esa misma clase principal de main pon:
            //     BlocksRegistry.registerAll();
            //     ItemsRegistry.registerAll();
            //     ViceburgerTabs.register();
        } catch (IOException e) {
            LOG.error("Error creando inicializador cliente: " + e.getMessage(), e);
            Messages.showErrorDialog("Error creando inicializador cliente: " + e.getMessage(), "Error");
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void updateBlocksRegistry(Project project, String modId, String modelName) {
        Path registryFile = Paths.get(
                project.getBasePath(),
                "src/main/java",
                "com", "tntstudios", modId, "registry",
                "BlocksRegistry.java"
        );
        if (!Files.exists(registryFile)) {
            // Si no existe el archivo, no hay nada que actualizar
            return;
        }
        try {
            List<String> lines = Files.readAllLines(registryFile, StandardCharsets.UTF_8);
            String constName = modelName.toUpperCase() + "_BLOCK";
            // Declaración que queremos insertar
            String declaration = String.format(
                    "    public static final Block %s = register(\"%s\", new %sBlock(AbstractBlock.Settings.create().strength(0.2f).nonOpaque()));",
                    constName, modelName, capitalize(modelName)
            );
            // Solo insertamos si aún no existe
            boolean already = lines.stream().anyMatch(l -> l.contains(constName));
            if (!already) {
                // Buscamos la línea con "registerAll" para insertar justo antes
                int idx = 0;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("public static void registerAll")) {
                        idx = i;
                        break;
                    }
                }
                lines.add(idx, declaration);
                Files.write(registryFile, lines, StandardCharsets.UTF_8);
                LOG.info("  • BlocksRegistry actualizado: " + constName);
            }
        } catch (IOException e) {
            LOG.error("Error actualizando BlocksRegistry: " + e.getMessage(), e);
            Messages.showErrorDialog("Error actualizando BlocksRegistry: " + e.getMessage(), "Error");
        }
    }

}
