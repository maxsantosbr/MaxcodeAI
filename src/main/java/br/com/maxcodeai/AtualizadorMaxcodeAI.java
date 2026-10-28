
package br.com.maxcodeai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

public class AtualizadorMaxcodeAI extends JDialog {

    public static final String VERSAO_ATUAL = "1.0.0";

    // Substituir pelo endereço real do arquivo version.txt no GitHub.
    private static final String URL_VERSAO =
            "https://raw.githubusercontent.com/maxsantosbr/MaxcodeAI-releases/main/version.txt";

    private final Color azul = new Color(40, 110, 220);
    private final Color verde = new Color(22, 150, 90);
    private final Color vermelho = new Color(200, 55, 55);

    private final JLabel lblStatus = new JLabel(
            "Clique em Verificar atualizações para começar.");

    private final JProgressBar progresso = new JProgressBar(0, 100);

    private final JButton btnVerificar =
            new JButton("Verificar atualizações");

    private final JButton btnFechar = new JButton("Fechar");

    public AtualizadorMaxcodeAI(Window owner) {
        super(owner, "Verificador de atualizações",
                Dialog.ModalityType.APPLICATION_MODAL);

        montarTela();
        setSize(560, 340);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(owner);
    }

    public static void abrir(Component pai) {
        Window owner = pai == null
                ? null
                : javax.swing.SwingUtilities.getWindowAncestor(pai);

        new AtualizadorMaxcodeAI(owner).setVisible(true);
    }

    private void montarTela() {
        JPanel raiz = new JPanel(new BorderLayout(0, 14));
        raiz.setBackground(Color.WHITE);
        raiz.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));

        JPanel topo = new JPanel();
        topo.setOpaque(false);
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));

        JLabel titulo = new JLabel("Atualizações do Maxcode AI");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 21));
        titulo.setForeground(new Color(35, 45, 60));
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitulo = new JLabel(
                "Verifique se existe uma versão mais recente.");
        subtitulo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitulo.setForeground(new Color(110, 120, 135));
        subtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel linhaVersao = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 6, 0));
        linhaVersao.setOpaque(false);

        JLabel rotuloVersao = new JLabel("Versão instalada:");
        rotuloVersao.setForeground(new Color(90, 100, 115));

        JLabel valorVersao = new JLabel(VERSAO_ATUAL);
        valorVersao.setFont(new Font("SansSerif", Font.BOLD, 13));
        valorVersao.setForeground(azul);

        linhaVersao.add(rotuloVersao);
        linhaVersao.add(valorVersao);

        topo.add(titulo);
        topo.add(Box.createVerticalStrut(6));
        topo.add(subtitulo);
        topo.add(Box.createVerticalStrut(16));
        topo.add(linhaVersao);

        raiz.add(topo, BorderLayout.NORTH);

        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));

        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(110, 120, 135));
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        progresso.setStringPainted(true);
        progresso.setString("Aguardando");
        progresso.setValue(0);
        progresso.setAlignmentX(Component.CENTER_ALIGNMENT);
        progresso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        centro.add(Box.createVerticalGlue());
        centro.add(lblStatus);
        centro.add(Box.createVerticalStrut(16));
        centro.add(progresso);
        centro.add(Box.createVerticalGlue());

        raiz.add(centro, BorderLayout.CENTER);

        btnVerificar.addActionListener(e -> verificarAtualizacao());
        btnFechar.addActionListener(e -> dispose());

        JPanel rodape = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rodape.setOpaque(false);
        rodape.add(btnFechar);
        rodape.add(btnVerificar);

        raiz.add(rodape, BorderLayout.SOUTH);

        setContentPane(raiz);
        getRootPane().setDefaultButton(btnVerificar);
    }

    private void verificarAtualizacao() {
        btnVerificar.setEnabled(false);
        progresso.setIndeterminate(true);
        progresso.setString("Verificando...");
        status("Consultando o servidor de atualizações...", azul);

        SwingWorker<String, Void> worker =
                new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() throws Exception {
                HttpURLConnection conexao =
                        (HttpURLConnection) new URL(URL_VERSAO)
                                .openConnection();

                conexao.setConnectTimeout(8000);
                conexao.setReadTimeout(8000);
                conexao.setRequestProperty(
                        "User-Agent", "MaxcodeAI-Updater");

                try {
                    int codigo = conexao.getResponseCode();

                    if (codigo != HttpURLConnection.HTTP_OK) {
                        throw new java.io.IOException(
                                "Servidor respondeu com HTTP " + codigo);
                    }

                    try (BufferedReader leitor = new BufferedReader(
                            new InputStreamReader(
                                    conexao.getInputStream(),
                                    StandardCharsets.UTF_8))) {

                        String versao = leitor.readLine();

                        if (versao == null || versao.trim().isEmpty()) {
                            throw new java.io.IOException(
                                    "O arquivo de versão está vazio.");
                        }

                        return versao.trim();
                    }
                } finally {
                    conexao.disconnect();
                }
            }

            @Override
            protected void done() {
                progresso.setIndeterminate(false);
                progresso.setValue(0);
                progresso.setString("Aguardando");

                try {
                    String remota = get();

                    if (compararVersoes(remota, VERSAO_ATUAL) > 0) {
                        status("Nova versão disponível: " + remota, verde);

                        JOptionPane.showMessageDialog(
                                AtualizadorMaxcodeAI.this,
                                "Nova versão: " + remota + "\n"
                                + "Versão instalada: " + VERSAO_ATUAL
                                + "\n\nO mecanismo de download será "
                                + "configurado na próxima etapa.",
                                "Atualização disponível",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        status("Você já está usando a versão mais recente.",
                                verde);
                    }

                } catch (Exception ex) {
                    status("Não foi possível verificar as atualizações.",
                            vermelho);

                    JOptionPane.showMessageDialog(
                            AtualizadorMaxcodeAI.this,
                            "Falha na verificação.\n"
                            + "Confira a URL do arquivo version.txt "
                            + "e sua conexão com a internet.\n\n"
                            + "Detalhes: " + mensagemErro(ex),
                            "Erro",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnVerificar.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void status(String texto, Color cor) {
        lblStatus.setText(texto);
        lblStatus.setForeground(cor);
    }

    private static String mensagemErro(Exception ex) {
        Throwable causa = ex;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa.getMessage() == null
                ? causa.getClass().getSimpleName()
                : causa.getMessage();
    }

    private static int compararVersoes(String a, String b) {
        String[] va = partesVersao(a);
        String[] vb = partesVersao(b);

        int tamanho = Math.max(va.length, vb.length);

        for (int i = 0; i < tamanho; i++) {
            int na = i < va.length ? Integer.parseInt(va[i]) : 0;
            int nb = i < vb.length ? Integer.parseInt(vb[i]) : 0;

            if (na != nb) {
                return Integer.compare(na, nb);
            }
        }

        return 0;
    }

    private static String[] partesVersao(String versao) {
        if (versao == null
                || !versao.trim().matches("\\d+(\\.\\d+)*")) {
            throw new IllegalArgumentException(
                    "Formato de versão inválido: " + versao);
        }

        return versao.trim().split("\\.");
    }
}
