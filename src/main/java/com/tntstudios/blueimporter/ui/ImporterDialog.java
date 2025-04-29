package com.tntstudios.blueimporter.ui;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ImporterDialog extends DialogWrapper {
    private JPanel content;

    private JComboBox<String> platformCombo;
    private JCheckBox geckoCheck;
    private JComboBox<String> versionCombo;
    private JButton analyzeBtn;
    private JButton generateBtn;

    public ImporterDialog() {
        super(true);
        init();
        setTitle("Blue Item/Block Importer");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        // Construye tu panel: labels + combos + botones
        content = new JPanel();
        // Layout, añadir:
        platformCombo = new JComboBox<>(new String[]{"Fabric"});
        geckoCheck    = new JCheckBox("Usar GeckoLib");
        versionCombo  = new JComboBox<>(new String[]{"1.20.1"});
        analyzeBtn    = new JButton("Analizar modelos");
        generateBtn   = new JButton("Generar código");

        content.add(new JLabel("Plataforma:"));   content.add(platformCombo);
        content.add(new JLabel("GeckoLib:"));     content.add(geckoCheck);
        content.add(new JLabel("Versión:"));      content.add(versionCombo);
        content.add(analyzeBtn);
        content.add(generateBtn);

        return content;
    }
}
