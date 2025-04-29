package com.tntstudios.blueimporter.ui;

import com.tntstudios.blueimporter.generator.CodeGenerator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        analyzeBtn.addActionListener(e -> doAnalyze());
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
        for (String tipo : new String[]{"block", "item"}) {
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
            if (displayName == null) return; // usuario canceló

            // aquí delegamos TODO en el CodeGenerator
            CodeGenerator.generate(
                    project,
                    modId,
                    tipo,
                    modelName,
                    displayName,
                    textures
            );
        }

        Messages.showInfoMessage("Generación completada.", "OK");
    }

    private Map<String,String> parseTextures(Project project, String modId, String tipo, String modelName) {
        String path = project.getBasePath()
                + "/src/main/resources/assets/" + modId
                + "/models/" + tipo + "/" + modelName + ".json";
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) return Collections.emptyMap();

        try {
            String text = new String(file.contentsToByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            Map<String,String> map = new LinkedHashMap<>();
            if (obj.has("textures")) {
                JsonObject tex = obj.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> e : tex.entrySet()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
            return map;
        } catch (Exception ex) {
            Messages.showErrorDialog("Error parseando JSON: " + ex.getMessage(), "Error");
            return Collections.emptyMap();
        }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        content = new JPanel(new BorderLayout(5,5));

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

        listModel = new DefaultListModel<>();
        modelList = new JList<>(listModel);
        content.add(new JScrollPane(modelList), BorderLayout.CENTER);

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
