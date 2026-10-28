
package br.com.maxcodeai;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class AgenteProgramador {

    private static final Path RAIZ = Paths.get(
            System.getProperty("user.home"),
            "MaxcodeAI",
            "Projetos"
    ).toAbsolutePath().normalize();

    private static final Pattern PACOTE = Pattern.compile(
            "(?m)^\\s*package\\s+([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*)\\s*;"
    );

    private AgenteProgramador() {
    }

    public static Path getPastaProjetos() {
        return RAIZ;
    }

    /**
     * Cria uma pasta isolada para um novo projeto.
     * @param nome
     * @return 
     * @throws java.io.IOException
     */
    public static Path criarProjeto(String nome) throws IOException {
        validarNome(nome);

        Files.createDirectories(RAIZ);

        Path projeto = RAIZ.resolve(nome).normalize();

        if (!projeto.startsWith(RAIZ)) {
            throw new SecurityException("Caminho de projeto inválido.");
        }

        if (Files.exists(projeto)) {

    if (!Files.isDirectory(projeto)) {
        throw new IOException(
                "O caminho existe, mas não é uma pasta: " + projeto
        );
    }

    // Reutiliza a pasta existente sem apagar os arquivos.
    Files.createDirectories(projeto.resolve("src"));

    return projeto;
}

Files.createDirectories(projeto);
Files.createDirectories(projeto.resolve("src"));

return projeto;
    }

    /**
     * Grava um arquivo dentro da pasta de um projeto.
     * O caminho não pode escapar da pasta do projeto.
     * @return 
     */
    public static Path gravarArquivo(
            Path projeto,
            String caminhoRelativo,
            String conteudo) throws IOException {

        Path raiz = validarProjeto(projeto);

        if (caminhoRelativo == null || caminhoRelativo.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o caminho do arquivo.");
        }

        Path destino = raiz.resolve(caminhoRelativo).normalize();

        if (!destino.startsWith(raiz)) {
            throw new SecurityException(
                    "Não é permitido gravar fora da pasta do projeto."
            );
        }

        if (!destino.toString().toLowerCase().endsWith(".java")) {
            throw new SecurityException(
                    "Esta primeira versão só permite gravar arquivos .java."
            );
        }

        if (conteudo == null) {
            throw new IllegalArgumentException("O conteúdo não foi informado.");
        }

        Files.createDirectories(destino.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(
                destino,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

            writer.write(conteudo);
        }

        return destino;
    }

    /**
     * Lê um arquivo Java pertencente ao projeto.
     */
    public static String lerArquivo(
            Path projeto,
            String caminhoRelativo) throws IOException {

        Path raiz = validarProjeto(projeto);
        Path arquivo = raiz.resolve(caminhoRelativo).normalize();

        if (!arquivo.startsWith(raiz)) {
            throw new SecurityException("Caminho fora do projeto.");
        }

        if (!arquivo.toString().toLowerCase().endsWith(".java")) {
            throw new SecurityException("Somente arquivos .java são permitidos.");
        }

        if (!Files.isRegularFile(arquivo)) {
            throw new IOException("Arquivo não encontrado: " + arquivo);
        }

        return new String(
                Files.readAllBytes(arquivo),
                StandardCharsets.UTF_8
        );
    }

    /**
     * Compila todos os arquivos Java do projeto.
     * Requer um JDK instalado, não apenas um JRE.
     */
    public static String compilar(Path projeto) throws IOException,
            InterruptedException {

        Path raiz = validarProjeto(projeto);
        Path fonte = raiz.resolve("src");
        Path saida = raiz.resolve("build");

        if (!Files.isDirectory(fonte)) {
            return "ERRO: A pasta src não existe.";
        }

        List<String> arquivos = new ArrayList<String>();

        try (java.util.stream.Stream<Path> stream = Files.walk(fonte)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> arquivos.add(p.toString()));
        }

        if (arquivos.isEmpty()) {
            return "ERRO: Nenhum arquivo Java encontrado em src.";
        }

        Files.createDirectories(saida);

        String javaHome = System.getProperty("java.home");
        Path compilador = Paths.get(javaHome, "bin", "javac.exe");

        if (!Files.isRegularFile(compilador)) {
            Path pai = Paths.get(javaHome).getParent();

            if (pai != null) {
                compilador = pai.resolve("bin").resolve("javac.exe");
            }
        }

        if (compilador == null || !Files.isRegularFile(compilador)) {
            return "ERRO: javac.exe não encontrado. Configure o NetBeans "
                    + "para usar um JDK completo.";
        }

        List<String> comando = new ArrayList<String>();
        comando.add(compilador.toString());
        comando.add("-encoding");
        comando.add("UTF-8");
        comando.add("-d");
        comando.add(saida.toString());
        comando.addAll(arquivos);

        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.directory(raiz.toFile());
        pb.redirectErrorStream(true);

        Process processo = pb.start();

        StringBuilder log = new StringBuilder();

        Thread leitor = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                processo.getInputStream(),
                                StandardCharsets.UTF_8))) {

                    String linha;

                    while ((linha = reader.readLine()) != null) {
                        synchronized (log) {
                            log.append(linha).append(System.lineSeparator());
                        }
                    }
                } catch (IOException ex) {
                    synchronized (log) {
                        log.append("Erro ao ler compilação: ")
                           .append(ex.getMessage())
                           .append(System.lineSeparator());
                    }
                }
            }
        });

        leitor.start();

        boolean terminou = processo.waitFor(60, TimeUnit.SECONDS);

        if (!terminou) {
            processo.destroyForcibly();
            leitor.join(3000);
            return "ERRO: Tempo limite de compilação excedido.";
        }

        leitor.join(5000);

        if (processo.exitValue() == 0) {
            return "COMPILAÇÃO CONCLUÍDA COM SUCESSO.\n"
                    + "Arquivos .class gerados em: " + saida
                    + "\n" + log.toString();
        }

        return "ERRO DE COMPILAÇÃO:\n" + log.toString();
    }

    private static Path validarProjeto(Path projeto) throws IOException {
        if (projeto == null) {
            throw new IllegalArgumentException("Projeto não informado.");
        }

        Path raiz = projeto.toAbsolutePath().normalize();

        if (!raiz.startsWith(RAIZ) || !Files.isDirectory(raiz)) {
            throw new SecurityException(
                    "O caminho não corresponde a um projeto autorizado."
            );
        }

        return raiz;
    }

    private static void validarNome(String nome) {
        if (nome == null
                || !nome.matches("[a-zA-Z0-9_-]{1,50}")) {
            throw new IllegalArgumentException(
                    "Use apenas letras, números, hífen e sublinhado no nome."
            );
        }
    }
}
