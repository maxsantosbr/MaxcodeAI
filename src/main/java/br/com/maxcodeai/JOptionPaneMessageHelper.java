
package br.com.maxcodeai;

import javax.swing.JOptionPane;

public final class JOptionPaneMessageHelper {

    private JOptionPaneMessageHelper() {
    }

    public static void showError(String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "MAXCODE AI",
                JOptionPane.ERROR_MESSAGE
        );
    }
}
