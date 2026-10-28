
package br.com.maxcodeai;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.prefs.Preferences;

public final class ThemeManager {

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ThemeManager.class);

    private static final String THEME_KEY = "theme";

    private ThemeManager() {
    }

    public static String getSavedTheme() {
        return PREFS.get(THEME_KEY, "Escuro");
    }

    public static void applyTheme(String themeName, JFrame window) {
        try {
            switch (themeName) {
                case "Claro":
                    FlatLightLaf.setup();
                    break;

                case "Darcula":
                    FlatDarculaLaf.setup();
                    break;

                case "IntelliJ":
                    FlatIntelliJLaf.setup();
                    break;

                case "Escuro":
                default:
                    FlatDarkLaf.setup();
                    themeName = "Escuro";
                    break;
            }

            PREFS.put(THEME_KEY, themeName);

            if (window != null) {
                SwingUtilities.updateComponentTreeUI(window);
                window.invalidate();
                window.validate();
                window.repaint();
            }

        } catch (Exception e) {
            e.printStackTrace();

            JOptionPaneMessageHelper.showError(
                    "Não foi possível alterar o tema."
            );
        }
    }
}
