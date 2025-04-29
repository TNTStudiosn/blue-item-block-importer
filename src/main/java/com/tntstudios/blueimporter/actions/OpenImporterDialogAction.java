package com.tntstudios.blueimporter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.tntstudios.blueimporter.ui.ImporterDialog;

public class OpenImporterDialogAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        // Crear y mostrar la ventana
        ImporterDialog dialog = new ImporterDialog();
        dialog.show();
    }
}
