
package br.com.maxcodeai;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class PreparadorIA extends JDialog {

    private static final String INSTALADOR_URL =
            "https://ollama.com/download/OllamaSetup.exe";

    private static final String API =
            "http://localhost:11434/api/tags";

    private static final String MODELO =
            "qwen2.5-coder:7b";

    private final JLabel status =
            new JLabel("Pronto para preparar a IA.");

    private final JTextArea log = new JTextArea();

    private final JProgressBar progresso =
            new JProgressBar(0, 100);

    private final JButton iniciar =
            new JButton("Preparar IA");

    private final JButton fechar =
            new JButton("Fechar");

    private boolean executando;

    public PreparadorIA(JFrame pai) {
        super(pai, "MAXCODE AI - Preparação", true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(650, 430);
        setMinimumSize(new Dimension(540, 340));
        setLocationRelativeTo(pai);

        montarInterface();

        iniciar.addActionListener(e -> iniciarPreparacao());
        fechar.addActionListener(e -> dispose());
    }

    private void montarInterface() {
        JPanel principal = new JPanel(new BorderLayout(10, 10));
        principal.setBorder(
                BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel titulo = new JLabel(
                "Preparação da inteligência artificial");

        titulo.setFont(new Font("SansSerif", Font.BOLD, 20));

        JPanel cabecalho = new JPanel(new BorderLayout(5, 8));
        cabecalho.add(titulo, BorderLayout.NORTH);
        cabecalho.add(status, BorderLayout.SOUTH);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font("Monospaced", Font.PLAIN, 12));

        progresso.setStringPainted(true);
        progresso.setString("Aguardando");

        JPanel botoes = new JPanel(
                new FlowLayout(FlowLayout.RIGHT));

        botoes.add(fechar);
        botoes.add(iniciar);

        JPanel rodape = new JPanel(new BorderLayout(5, 8));
        rodape.add(progresso, BorderLayout.NORTH);
        rodape.add(botoes, BorderLayout.SOUTH);

        principal.add(cabecalho, BorderLayout.NORTH);
        principal.add(new JScrollPane(log), BorderLayout.CENTER);
        principal.add(rodape, BorderLayout.SOUTH);

        setContentPane(principal);
    }

    private void registrar(String mensagem) {
        log.append(mensagem + "\n");
        log.setCaretPosition(log.getDocument().getLength());
        status.setText(mensagem);
    }

    private void iniciarPreparacao() {
        if (executando) {
            return;
        }

        executando = true;
        iniciar.setEnabled(false);
        fechar.setEnabled(false);
        iniciar.setText("Preparando...");
        log.setText("");
        progresso.setValue(0);
        progresso.setIndeterminate(true);
        progresso.setString("Em andamento");

        new SwingWorker<Void, String>() {

            @Override
            protected Void doInBackground() throws Exception {
                preparar();
                return null;
            }

            @Override
            protected void process(List<String> mensagens) {
                for (String mensagem : mensagens) {
                    registrar(mensagem);
                }
            }

            @Override
            protected void done() {
                executando = false;
                progresso.setIndeterminate(false);

                try {
                    get();

                    progresso.setValue(100);
                    progresso.setString("Concluído");

                    registrar(
                            "Preparação concluída. A IA está pronta.");

                    iniciar.setText("Concluído");

                } catch (Exception ex) {
                    progresso.setString("Falha");

                    Throwable causa = ex.getCause();
                    String mensagem = causa != null
                            ? causa.getMessage()
                            : ex.getMessage();

                    registrar("ERRO: " + mensagem);
                    iniciar.setText("Tentar novamente");
                }

                iniciar.setEnabled(true);
                fechar.setEnabled(true);
            }

            private void preparar() throws Exception {
                publish("Verificando se o Ollama está instalado...");

                if (!apiDisponivel()) {
                    Path executavel = localizarOllama();

                    if (executavel == null) {
                        instalarOllama();
                        executavel = localizarOllama();
                    }

                    if (executavel == null) {
                        throw new Exception(
                                "O instalador terminou, mas o "
                                + "executável do Ollama não foi encontrado.");
                    }

                    if (!apiDisponivel()) {
                        publish("Iniciando o mecanismo de IA...");
                        iniciarServidor(executavel);
                    }

                    if (!aguardarAPI(120)) {
                        throw new Exception(
                                "O Ollama não respondeu após a instalação. "
                                + "Tente novamente.");
                    }
                }

                publish("O mecanismo está funcionando.");

                if (modeloDisponivel()) {
                    publish("O modelo já está instalado.");
                    return;
                }

                publish("Baixando o modelo de programação "
                        + MODELO + "...");

                setIndeterminate(true);

                Path executavel = localizarOllama();

                if (executavel == null) {
                    throw new Exception(
                            "Não foi possível localizar o Ollama.");
                }

                executarComando(
                        Arrays.asList(
                                executavel.toString(),
                                "pull",
                                MODELO),
                        120);

                if (!modeloDisponivel()) {
                    throw new Exception(
                            "Não foi possível confirmar a instalação "
                            + "do modelo.");
                }

                publish("Modelo instalado e confirmado.");
            }

            private void instalarOllama() throws Exception {
                publish("Baixando o instalador oficial...");

                Path instalador = Files.createTempFile(
                        "MAXCODEAI-Ollama-", ".exe");

                try {
                    baixarArquivo(instalador);

                    publish("Verificando a assinatura digital...");
                    verificarAssinatura(instalador);

                    publish("Instalando o Ollama...");

                    Process processo = new ProcessBuilder(
                            instalador.toString(),
                            "/VERYSILENT",
                            "/NORESTART",
                            "/SUPPRESSMSGBOXES")
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start();

                    if (!processo.waitFor(5, TimeUnit.MINUTES)) {
                        processo.destroyForcibly();

                        throw new Exception(
                                "A instalação excedeu cinco minutos.");
                    }

                    if (processo.exitValue() != 0) {
                        throw new Exception(
                                "O instalador terminou com código "
                                + processo.exitValue());
                    }

                    publish("Instalador concluído.");

                } finally {
                    Files.deleteIfExists(instalador);
                }
            }

            private void baixarArquivo(Path destino)
                    throws Exception {

                HttpURLConnection conexao = null;

                try {
                    conexao = (HttpURLConnection)
                            URI.create(INSTALADOR_URL)
                                    .toURL().openConnection();

                    conexao.setConnectTimeout(30000);
                    conexao.setReadTimeout(60000);
                    conexao.setInstanceFollowRedirects(true);

                    int codigo = conexao.getResponseCode();

                    if (codigo != HttpURLConnection.HTTP_OK) {
                        throw new Exception(
                                "Falha no download. Código HTTP: "
                                + codigo);
                    }

                    long total = conexao.getContentLengthLong();
                    long recebido = 0;

                    try (InputStream entrada =
                                 conexao.getInputStream()) {

                        try (var saida =
                                     Files.newOutputStream(destino)) {

                            byte[] buffer = new byte[65536];
                            int quantidade;

                            while ((quantidade = entrada.read(buffer))
                                    != -1) {

                                if (isCancelled()) {
                                    throw new Exception(
                                            "Operação cancelada.");
                                }

                                saida.write(buffer, 0, quantidade);
                                recebido += quantidade;

                                if (total > 0) {
                                    int percentual = (int)
                                            (recebido * 100 / total);

                                    setProgress(percentual);
                                }
                            }
                        }
                    }

                    if (Files.size(destino) == 0) {
                        throw new Exception(
                                "O instalador baixado está vazio.");
                    }

                } finally {
                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            private void verificarAssinatura(Path arquivo)
                    throws Exception {

                String caminho = arquivo.toAbsolutePath()
                        .toString().replace("'", "''");

                String script =
                        "$s = Get-AuthenticodeSignature -LiteralPath '"
                        + caminho
                        + "'; if ($s.Status -eq 'Valid') "
                        + "{ exit 0 } else { exit 1 }";

                Process processo = new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        script)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();

                if (!processo.waitFor(30, TimeUnit.SECONDS)) {
                    processo.destroyForcibly();

                    throw new Exception(
                            "Tempo esgotado ao verificar a assinatura.");
                }

                if (processo.exitValue() != 0) {
                    throw new Exception(
                            "A assinatura digital não foi validada. "
                            + "O instalador não será executado.");
                }
            }

            private Path localizarOllama() {
                String local = System.getenv("LOCALAPPDATA");
                String programas = System.getenv("ProgramFiles");

                Path[] caminhos = {
                    local == null ? null
                        : Paths.get(local, "Programs",
                                "Ollama", "ollama.exe"),
                    programas == null ? null
                        : Paths.get(programas, "Ollama", "ollama.exe")
                };

                for (Path caminho : caminhos) {
                    if (caminho != null
                            && Files.isRegularFile(caminho)) {
                        return caminho;
                    }
                }

                return null;
            }

            private void iniciarServidor(Path executavel)
                    throws Exception {

                Path pastaLog = Paths.get(
                        System.getenv("LOCALAPPDATA"),
                        "MAXCODEAI");

                Files.createDirectories(pastaLog);

                Path arquivoLog =
                        pastaLog.resolve("ollama-server.log");

                new ProcessBuilder(
                        executavel.toString(), "serve")
                        .redirectErrorStream(true)
                        .redirectOutput(arquivoLog.toFile())
                        .start();
            }

            private boolean aguardarAPI(int segundos)
                    throws InterruptedException {

                for (int i = 0; i < segundos; i++) {
                    if (apiDisponivel()) {
                        return true;
                    }

                    Thread.sleep(1000);
                }

                return false;
            }

            private boolean apiDisponivel() {
                HttpURLConnection conexao = null;

                try {
                    conexao = (HttpURLConnection)
                            URI.create(API).toURL().openConnection();

                    conexao.setConnectTimeout(2000);
                    conexao.setReadTimeout(2000);
                    conexao.setRequestMethod("GET");

                    return conexao.getResponseCode() == 200;

                } catch (Exception ex) {
                    return false;
                } finally {
                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            private boolean modeloDisponivel() throws Exception {
                HttpURLConnection conexao = (HttpURLConnection)
                        URI.create(API).toURL().openConnection();

                conexao.setConnectTimeout(5000);
                conexao.setReadTimeout(5000);

                try {
                    if (conexao.getResponseCode() != 200) {
                        return false;
                    }

                    try (InputStream entrada =
                                 conexao.getInputStream()) {

                        String resposta = new String(
                                entrada.readAllBytes(),
                                StandardCharsets.UTF_8);

                        return resposta.contains(MODELO);
                    }
                } finally {
                    conexao.disconnect();
                }
            }

            private void executarComando(
                    List<String> comando,
                    int minutosLimite) throws Exception {

                publish("Preparando o download do modelo...");

                Path pastaLog = Paths.get(
                        System.getenv("LOCALAPPDATA"),
                        "MAXCODEAI");

                Files.createDirectories(pastaLog);

                Path arquivoLog =
                        pastaLog.resolve("download-modelo.log");

                Process processo = new ProcessBuilder(comando)
                        .redirectErrorStream(true)
                        .redirectOutput(arquivoLog.toFile())
                        .start();

                long inicio = System.nanoTime();

                while (processo.isAlive()) {
                    if (isCancelled()) {
                        processo.destroyForcibly();
                        throw new Exception("Operação cancelada.");
                    }

                    long decorrido = TimeUnit.NANOSECONDS.toMinutes(
                            System.nanoTime() - inicio);

                    if (decorrido >= minutosLimite) {
                        processo.destroyForcibly();

                        throw new Exception(
                                "O download excedeu o tempo limite. "
                                + "Tente novamente.");
                    }

                    Thread.sleep(1000);
                }

                if (processo.exitValue() != 0) {
                    throw new Exception(
                            "O download do modelo falhou. "
                            + "Verifique sua conexão e tente novamente. "
                            + "Log: " + arquivoLog);
                }
            }

            private void setIndeterminate(boolean valor) {
                SwingUtilities.invokeLater(() -> {
                    progresso.setIndeterminate(valor);

                    if (valor) {
                        progresso.setString("Baixando modelo...");
                    }
                });
            }

        }.execute();
    }

    public static void abrir(JFrame pai) {
        SwingUtilities.invokeLater(() -> {
            PreparadorIA tela = new PreparadorIA(pai);
            tela.setVisible(true);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PreparadorIA tela = new PreparadorIA(null);
            tela.setVisible(true);
        });
    }
}
