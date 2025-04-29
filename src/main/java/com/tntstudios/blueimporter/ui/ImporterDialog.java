package com.tntstudios.blueimporter.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ImporterDialog extends DialogWrapper {
    private JPanel content;

    private JTextField modIdField;
    private JComboBox<String> platformCombo;
    private JCheckBox geckoCheck;
    private JComboBox<String> versionCombo;
    private JButton analyzeBtn;
    private JButton generateBtn;

    private DefaultListModel<String> listModel;
    private JList<String> modelList;

    public ImporterDialog() {
        super(true);
        init();
        setTitle("Blue Item/Block Importer");

        // Listener para analizar modelos
        analyzeBtn.addActionListener(e -> doAnalyze());
        // Listener para generar los archivos
        generateBtn.addActionListener(e -> doGenerate());
    }

    private void doAnalyze() {
        String modId = modIdField.getText().trim();
        if (modId.isEmpty()) {
            Messages.showErrorDialog("Debes indicar el Mod ID (p.ej. viceburger).", "Error");
            return;
        }
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        String basePath = project.getBasePath() + "/src/main/resources/assets/" + modId + "/models";
        VirtualFile modelsRoot = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (modelsRoot == null) {
            Messages.showErrorDialog("No encontré carpetas de modelos en:\n" + basePath, "Error al Analizar");
            return;
        }

        listModel.clear();
        for (String tipo : new String[]{"block","item"}) {
            VirtualFile folder = modelsRoot.findChild(tipo);
            if (folder != null) {
                for (VirtualFile file : folder.getChildren()) {
                    if ("json".equalsIgnoreCase(file.getExtension())) {
                        listModel.addElement(tipo + "/" + file.getNameWithoutExtension());
                    }
                }
            }
        }
        if (listModel.isEmpty()) {
            Messages.showInfoMessage("No se encontraron modelos JSON en 'block' ni 'item'.", "Sin resultados");
        }
    }

    private void doGenerate() {
        String modId = modIdField.getText().trim();
        if (modId.isEmpty()) {
            Messages.showErrorDialog("Debes indicar el Mod ID antes de generar.", "Error");
            return;
        }
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        List<String> modelos = Collections.list(listModel.elements());
        if (modelos.isEmpty()) {
            Messages.showInfoMessage("No hay modelos para procesar. Primero analiza modelos.", "Atención");
            return;
        }

        for (String entry : modelos) {
            String[] parts = entry.split("/");
            String tipo = parts[0];
            String modelName = parts[1];
            Map<String,String> textures = parseTextures(project, modId, tipo, modelName);

            String displayName = Messages.showInputDialog(
                    "Nombre en Lang para '" + modelName + "'?",
                    "Renombrar Modelo",
                    null,
                    capitalize(modelName),
                    null
            );
            if (displayName == null) return; // cancelar

            WriteCommandAction.runWriteCommandAction(project, () -> {
                createLootTable(project, modId, modelName);
                createBlockstate(project, modId, modelName, textures);
                createBlockClass(project, modId, modelName);
                updateRegistry(project, modId, modelName);
            });
        }
        Messages.showInfoMessage("Generación completada.", "OK");
    }

    private Map<String,String> parseTextures(Project project, String modId, String tipo, String modelName) {
        String path = project.getBasePath() + "/src/main/resources/assets/" + modId + "/models/" + tipo + "/" + modelName + ".json";
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) return Collections.emptyMap();
        try {
            String text = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            Map<String,String> map = new HashMap<>();
            if (obj.has("textures")) {
                JsonObject tex = obj.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> e : tex.entrySet()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
            return map;
        } catch (IOException ex) {
            Messages.showErrorDialog("Error parseando JSON: " + ex.getMessage(), "Error");
            return Collections.emptyMap();
        }
    }

    // Métodos stub para generación de archivos
    private void createLootTable(Project project, String modId, String modelName) {
        // TODO: implementar escritura de loot table JSON
    }
    private void createBlockstate(Project project, String modId, String modelName, Map<String,String> textures) {
        // TODO: implementar escritura de blockstate JSON
    }
    private void createBlockClass(Project project, String modId, String modelName) {
        // TODO: implementar generación de clase Java
    }
    private void updateRegistry(Project project, String modId, String modelName) {
        // TODO: implementar actualización de archivo de registro
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        content = new JPanel(new BorderLayout(5,5));

        // Panel superior: configuración
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Mod ID:"));
        modIdField = new JTextField(15);
        top.add(modIdField);

        top.add(new JLabel("Plataforma:"));
        platformCombo = new JComboBox<>(new String[]{"Fabric"});
        top.add(platformCombo);

        geckoCheck = new JCheckBox("Usar GeckoLib");
        top.add(geckoCheck);

        top.add(new JLabel("Versión:"));
        versionCombo = new JComboBox<>(new String[]{"1.20.1"});
        top.add(versionCombo);

        content.add(top, BorderLayout.NORTH);

        // Centro: lista de modelos
        listModel = new DefaultListModel<>();
        modelList = new JList<>(listModel);
        content.add(new JScrollPane(modelList), BorderLayout.CENTER);

        // Sur: botones
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        analyzeBtn = new JButton("Analizar modelos");
        generateBtn = new JButton("Generar código");
        bottom.add(analyzeBtn);
        bottom.add(generateBtn);

        content.add(bottom, BorderLayout.SOUTH);

        return content;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
