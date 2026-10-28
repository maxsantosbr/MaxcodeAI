package br.com.maxcodeai.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Atualizador do Maxcode AI.
 *
 * Verifica a versão publicada, baixa o instalador mostrando o progresso
 * e executa o instalador após a conclusão.
 */
public class Atualizador extends JDialog {

    private static final String VERSAO_ATUAL = "1.0.0";

    /*
     * SUBSTITUA estas URLs pelas URLs reais do seu servidor.
     *
     * version.txt deve conter somente a versão publicada, por exemplo:
     * 1.1.0
     */
    private static final String URL_VERSION =
            "https://raw.githubusercontent.com/maxsantosbr/MaxcodeAI-releases/main/version.txt";

    private static final String URL_INSTALADOR =
            "https://github.com/maxsantosbr/MaxcodeAI-releases/releases/latest/download/MaxcodeAI.exe";

    private final JLabel lblStatus = new JLabel(
            "Pronto para verificar atualizações.");

    private final JLabel lblVersao = new JLabel(
            "Versão instalada: " + VERSAO_ATUAL);

    private final JProgressBar barraProgresso = new JProgressBar(0, 100);

    private final JButton btnAtualizar = new JButton("Verificar atualização");

    private final JButton btnFechar = new JButton("Fechar");

    private final JTextArea txtDetalhes = new JTextArea(3, 30);

    private boolean atualizacaoEmAndamento = false;

    public Atualizador(Window proprietario) {
        super(proprietario, "Atualizador do Maxcode AI", ModalityType.APPLICATION_MODAL);

        configurarJanela();
        montarInterface();
        configurarEventos();
    }

    /**
     * Abre a janela do atualizador.
     * @param proprietario
     */
    public static void abrir(Window proprietario) {
        SwingUtilities.invokeLater(() -> {
            Atualizador atualizador = new Atualizador(proprietario);
            atualizador.setVisible(true);
        });
    }

    private void configurarJanela() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(520, 330);
        setResizable(false);
        setLocationRelativeTo(getOwner());
    }

    private void montarInterface() {
        JPanel principal = new JPanel(new BorderLayout(15, 15));
        principal.setBorder(new EmptyBorder(20, 22, 20, 22));
        principal.setBackground(UIManager.getColor("Panel.background"));

        // Cabeçalho
        JPanel cabecalho = new JPanel(new BorderLayout(12, 5));
        cabecalho.setOpaque(false);

        JLabel icone = new JLabel("\u21BB", SwingConstants.CENTER);
        icone.setFont(new Font("SansSerif", Font.BOLD, 38));
        icone.setPreferredSize(new Dimension(60, 60));
        icone.setForeground(UIManager.getColor("Component.accentColor"));

        JPanel textosCabecalho = new JPanel();
        textosCabecalho.setLayout(new BoxLayout(textosCabecalho, BoxLayout.Y_AXIS));
        textosCabecalho.setOpaque(false);

        JLabel titulo = new JLabel("Maxcode AI");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 22));

        JLabel subtitulo = new JLabel("Atualizações do aplicativo");
        subtitulo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitulo.setForeground(UIManager.getColor("Label.disabledForeground"));

        textosCabecalho.add(titulo);
        textosCabecalho.add(Box.createVerticalStrut(5));
        textosCabecalho.add(subtitulo);

        cabecalho.add(icone, BorderLayout.WEST);
        cabecalho.add(textosCabecalho, BorderLayout.CENTER);

        // Área central
        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setOpaque(false);

        lblStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblVersao.setAlignmentX(Component.LEFT_ALIGNMENT);

        barraProgresso.setStringPainted(true);
        barraProgresso.setValue(0);
        barraProgresso.setAlignmentX(Component.LEFT_ALIGNMENT);
        barraProgresso.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 22));

        txtDetalhes.setEditable(false);
        txtDetalhes.setFocusable(false);
        txtDetalhes.setLineWrap(true);
        txtDetalhes.setWrapStyleWord(true);
        txtDetalhes.setOpaque(false);
        txtDetalhes.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtDetalhes.setForeground(UIManager.getColor("Label.disabledForeground"));

        JScrollPane scrollDetalhes = new JScrollPane(txtDetalhes);
        scrollDetalhes.setBorder(null);
        scrollDetalhes.setOpaque(false);
        scrollDetalhes.getViewport().setOpaque(false);
        scrollDetalhes.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollDetalhes.setPreferredSize(new Dimension(450, 45));

        centro.add(lblVersao);
        centro.add(Box.createVerticalStrut(12));
        centro.add(lblStatus);
        centro.add(Box.createVerticalStrut(12));
        centro.add(barraProgresso);
        centro.add(Box.createVerticalStrut(8));
        centro.add(scrollDetalhes);

        // Botões
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rodape.setOpaque(false);

        rodape.add(btnAtualizar);
        rodape.add(btnFechar);

        principal.add(cabecalho, BorderLayout.NORTH);
        principal.add(centro, BorderLayout.CENTER);
        principal.add(rodape, BorderLayout.SOUTH);

        setContentPane(principal);
    }

    private void configurarEventos() {
        btnAtualizar.addActionListener(e -> verificarAtualizacao());

        btnFechar.addActionListener(e -> {
            if (!atualizacaoEmAndamento) {
                dispose();
            }
        });
    }

    /**
     * Consulta a versão disponível no servidor.
     */
    private void verificarAtualizacao() {
        if (atualizacaoEmAndamento) {
            return;
        }

        atualizarEstado(true);
        barraProgresso.setIndeterminate(true);
        lblStatus.setText("Verificando atualizações...");
        txtDetalhes.setText("");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                HttpURLConnection conexao = null;

                try {
                    conexao = (HttpURLConnection) new URL(URL_VERSION)
                            .openConnection();

                    conexao.setRequestMethod("GET");
                    conexao.setConnectTimeout(10000);
                    conexao.setReadTimeout(10000);
                    conexao.setRequestProperty(
                            "User-Agent", "MaxcodeAI-Updater");

                    int codigo = conexao.getResponseCode();

                    if (codigo != HttpURLConnection.HTTP_OK) {
                        throw new IllegalStateException(
                                "O servidor respondeu com HTTP " + codigo);
                    }

                    try (BufferedReader leitor = new BufferedReader(
                            new InputStreamReader(
                                    conexao.getInputStream(),
                                    StandardCharsets.UTF_8))) {

                        String versao = leitor.readLine();

                        if (versao == null || versao.trim().isEmpty()) {
                            throw new IllegalStateException(
                                    "O arquivo version.txt está vazio.");
                        }

                        return versao.trim();
                    }
                } finally {
                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            @Override
            protected void done() {
                barraProgresso.setIndeterminate(false);

                try {
                    String versaoRemota = get();

                    if (!versaoValida(versaoRemota)) {
                        throw new IllegalStateException(
                                "Formato de versão inválido: " + versaoRemota);
                    }

                    if (compararVersoes(versaoRemota, VERSAO_ATUAL) > 0) {
                        lblStatus.setText("Nova versão disponível: " + versaoRemota);
                        txtDetalhes.setText(
                                "Versão instalada: " + VERSAO_ATUAL
                                + "\nNova versão: " + versaoRemota
                                + "\nClique em Atualizar para baixar o instalador.");

                        btnAtualizar.setText("Atualizar");
                        btnAtualizar.removeActionListener(
                                btnAtualizar.getActionListeners()[0]);

                        btnAtualizar.addActionListener(e -> baixarAtualizacao());

                        atualizarEstado(false);
                    } else {
                        lblStatus.setText("Você já está usando a versão mais recente: " + VERSAO_ATUAL);
                        txtDetalhes.setText("Nenhuma atualização necessária.");
                        btnAtualizar.setText("Verificar novamente");
                        atualizarEstado(false);
                    }
                } catch (Exception ex) {
                    mostrarErro("Não foi possível verificar a atualização.",
                            causaRaiz(ex));
                    lblStatus.setText("Falha ao verificar atualização.");
                    atualizarEstado(false);
                }
            }
        };

        worker.execute();
    }

    /**
     * Baixa o instalador e atualiza a barra de progresso.
     */
    private void baixarAtualizacao() {
        if (atualizacaoEmAndamento) {
            return;
        }

        atualizarEstado(true);
        barraProgresso.setValue(0);
        barraProgresso.setIndeterminate(false);
        lblStatus.setText("Baixando atualização...");
        txtDetalhes.setText("Aguarde até o download terminar.");

        SwingWorker<Path, Integer> worker = new SwingWorker<Path, Integer>() {
            @Override
            protected Path doInBackground() throws Exception {
                HttpURLConnection conexao = null;

                Path destino = Paths.get(
                        System.getProperty("java.io.tmpdir"),
                        "MaxcodeAI-Setup.exe");

                try {
                    conexao = (HttpURLConnection) new URL(URL_INSTALADOR)
                            .openConnection();

                    conexao.setRequestMethod("GET");
                    conexao.setConnectTimeout(15000);
                    conexao.setReadTimeout(30000);
                    conexao.setRequestProperty(
                            "User-Agent", "MaxcodeAI-Updater");

                    int codigo = conexao.getResponseCode();

                    if (codigo != HttpURLConnection.HTTP_OK) {
                        throw new IllegalStateException(
                                "O servidor respondeu com HTTP " + codigo);
                    }

                    long tamanhoTotal = conexao.getContentLengthLong();
                    long totalLido = 0;

                    try (InputStream entrada = conexao.getInputStream()) {
                        Files.copy(
                                entrada,
                                destino,
                                StandardCopyOption.REPLACE_EXISTING);
                    }

                    if (!Files.exists(destino) || Files.size(destino) == 0) {
                        throw new IllegalStateException(
                                "O arquivo baixado está vazio.");
                    }

                    /*
                     * Files.copy não publica progresso por bloco.
                     * O arquivo foi baixado; o valor 100 é publicado
                     * somente após a conclusão.
                     */
                    publish(100);

                    return destino;
                } finally {
                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            @Override
            protected void process(java.util.List<Integer> progresso) {
                if (!progresso.isEmpty()) {
                    barraProgresso.setValue(
                            progresso.get(progresso.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    Path instalador = get();

                    lblStatus.setText("Download concluído.");
                    txtDetalhes.setText(
                            "O instalador foi salvo em:\n" + instalador);

                    int opcao = JOptionPane.showConfirmDialog(
                            Atualizador.this,
                            "O download foi concluído.\n"
                            + "Deseja executar o instalador agora?",
                            "Atualização do Maxcode AI",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);

                    if (opcao == JOptionPane.YES_OPTION) {
                        executarInstalador(instalador);
                    } else {
                        lblStatus.setText("Instalador baixado. Execução adiada.");
                        atualizarEstado(false);
                    }
                } catch (Exception ex) {
                    mostrarErro("Não foi possível baixar a atualização.",
                            causaRaiz(ex));
                    lblStatus.setText("Falha no download.");
                    atualizarEstado(false);
                }
            }
        };

        worker.execute();
    }

    /**
     * Executa o instalador baixado.
     */
    private void executarInstalador(Path instalador) {
        try {
            File arquivo = instalador.toFile();

            if (!arquivo.exists() || !arquivo.isFile()) {
                throw new IllegalStateException(
                        "O arquivo do instalador não foi encontrado.");
            }

            /*
             * Inicia o instalador no Windows.
             * O instalador pode precisar de confirmação do usuário
             * ou de permissões administrativas.
             */
            new ProcessBuilder(arquivo.getAbsolutePath())
                    .directory(arquivo.getParentFile())
                    .start();

            lblStatus.setText("Instalador iniciado.");
            dispose();

        } catch (Exception ex) {
            mostrarErro("Não foi possível executar o instalador.",
                    causaRaiz(ex));
            atualizarEstado(false);
        }
    }

    private void atualizarEstado(boolean ocupado) {
        atualizacaoEmAndamento = ocupado;
        btnAtualizar.setEnabled(!ocupado);
        btnFechar.setEnabled(!ocupado);
    }

    private boolean versaoValida(String versao) {
        return versao != null
                && versao.matches("\\d+(\\.\\d+)*");
    }

    /**
     * Compara versões numéricas, por exemplo:
     * 1.10.0 > 1.9.0
     *
     * Retorna valor positivo se a primeira versão for maior,
     * zero se forem iguais e negativo se for menor.
     */
    private int compararVersoes(String primeira, String segunda) {
        String[] a = primeira.split("\\.");
        String[] b = segunda.split("\\.");

        int quantidade = Math.max(a.length, b.length);

        for (int i = 0; i < quantidade; i++) {
            int numeroA = i < a.length ? Integer.parseInt(a[i]) : 0;
            int numeroB = i < b.length ? Integer.parseInt(b[i]) : 0;

            if (numeroA != numeroB) {
                return Integer.compare(numeroA, numeroB);
            }
        }

        return 0;
    }

    private String causaRaiz(Exception ex) {
        Throwable causa = ex;

        while (causa.getCause() != null) {
            causa = causa.getCause();
        }

        String mensagem = causa.getMessage();

        return mensagem == null || mensagem.trim().isEmpty()
                ? causa.getClass().getSimpleName()
                : mensagem;
    }

    private void mostrarErro(String titulo, String mensagem) {
        JOptionPane.showMessageDialog(
                this,
                titulo + "\n\nDetalhes: " + mensagem,
                "Atualizador do Maxcode AI",
                JOptionPane.ERROR_MESSAGE);
    }
}