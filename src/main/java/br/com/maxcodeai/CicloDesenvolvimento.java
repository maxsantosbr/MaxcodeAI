
package br.com.maxcodeai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CicloDesenvolvimento {

    private static final String URL_OLLAMA =
            "http://localhost:11434/api/chat";

    private static final String MODELO = "qwen2.5-coder:7b";
    private static final int MAX_CICLOS = 8;
    private static final int MAX_CARACTERES_FONTE = 25000;

    public interface Ouvinte {
        void aoAtualizar(String mensagem);
        void aoConcluir(String resultado);
        void aoPerguntar(String pergunta);
        void aoFalhar(String erro);
    }

    private final Ouvinte ouvinte;
    private volatile boolean cancelado;

    public CicloDesenvolvimento(Ouvinte ouvinte) {
        if (ouvinte == null) {
            throw new IllegalArgumentException("Ouvinte obrigatório.");
        }
        this.ouvinte = ouvinte;
    }

    public void cancelar() {
        cancelado = true;
    }

    public void iniciar(Path projeto, String objetivo) {
        try {
            if (objetivo == null || objetivo.trim().isEmpty()) {
                ouvinte.aoPerguntar("Descreva o software que deseja criar.");
                return;
            }

            if (projeto == null) {
                ouvinte.aoFalhar("O caminho do projeto não foi informado.");
                return;
            }

            Path pastaProjetos = AgenteProgramador.getPastaProjetos()
                    .toAbsolutePath().normalize();
            Path raiz = projeto.toAbsolutePath().normalize();

            if (!raiz.startsWith(pastaProjetos)
                    || raiz.equals(pastaProjetos)
                    || !Files.isDirectory(raiz)) {
                ouvinte.aoFalhar("Projeto não autorizado: " + raiz);
                return;
            }

            String feedbackCompilacao = "Ainda não compilado.";

            for (int ciclo = 1; ciclo <= MAX_CICLOS; ciclo++) {
                if (cancelado) {
                    ouvinte.aoConcluir("Desenvolvimento cancelado.");
                    return;
                }

                ouvinte.aoAtualizar("Ciclo " + ciclo + " de "
                        + MAX_CICLOS + ": analisando o projeto.");

                String arquivosProjeto = lerFontesProjeto(raiz);
                String prompt = criarPrompt(
                        objetivo, feedbackCompilacao, arquivosProjeto, ciclo);

                ouvinte.aoAtualizar("Enviando instruções ao modelo "
                        + MODELO + "...");

                String resposta = consultarOllama(prompt);

                if (cancelado) {
                    ouvinte.aoConcluir("Desenvolvimento cancelado.");
                    return;
                }

                if (resposta == null || resposta.trim().isEmpty()) {
                    ouvinte.aoFalhar("O Ollama retornou uma resposta vazia.");
                    return;
                }

                String acao = obterCampo(resposta, "ACAO");
                String mensagem = obterCampo(resposta, "MENSAGEM");

                if (acao == null) {
                    ouvinte.aoFalhar("Resposta fora do protocolo esperado:\n"
                            + limitarTexto(resposta, 3000));
                    return;
                }

                if ("PERGUNTAR".equalsIgnoreCase(acao)) {
                    String pergunta = mensagem == null ? "" : mensagem;
                    String perguntaMin = pergunta.toLowerCase();

                    boolean confirmacaoDesnecessaria =
                            perguntaMin.contains("deseja criar")
                            || perguntaMin.contains("quer criar")
                            || perguntaMin.contains("posso criar")
                            || perguntaMin.contains("deseja continuar")
                            || perguntaMin.contains("quer que eu crie");

                    if (confirmacaoDesnecessaria && ciclo < MAX_CICLOS) {
                        feedbackCompilacao =
                                "Não solicite confirmação para criar arquivos. "
                                + "O usuário já autorizou a tarefa. "
                                + "Execute o objetivo usando ACAO: GRAVAR.";
                        continue;
                    }

                    ouvinte.aoPerguntar(pergunta.isEmpty()
                            ? "A IA precisa de mais informações."
                            : pergunta);
                    return;
                }

                if ("CONCLUIR".equalsIgnoreCase(acao)) {
                    if (feedbackCompilacao.startsWith(
                            "COMPILAÇÃO CONCLUÍDA COM SUCESSO")) {
                        ouvinte.aoConcluir(mensagem == null
                                ? "Projeto compilado com sucesso."
                                : mensagem);
                    } else {
                        ouvinte.aoConcluir(
                                "O modelo informou que terminou, mas "
                                + "a compilação bem-sucedida não foi confirmada.\n"
                                + feedbackCompilacao);
                    }
                    return;
                }

                if ("GRAVAR".equalsIgnoreCase(acao)) {
                    String caminho = obterCampo(resposta, "CAMINHO");
                    String codigo = obterBloco(resposta);

                    if (caminho == null || codigo == null) {
                        ouvinte.aoFalhar(
                                "A IA não forneceu CAMINHO ou CONTEUDO válidos.");
                        return;
                    }

                    if (cancelado) {
                        ouvinte.aoConcluir("Desenvolvimento cancelado.");
                        return;
                    }

                    ouvinte.aoAtualizar("Gravando arquivo: " + caminho);
                    AgenteProgramador.gravarArquivo(raiz, caminho, codigo);

                    ouvinte.aoAtualizar("Compilando os arquivos Java...");
                    feedbackCompilacao = AgenteProgramador.compilar(raiz);
                    ouvinte.aoAtualizar(feedbackCompilacao);

                    if (feedbackCompilacao.startsWith(
                            "COMPILAÇÃO CONCLUÍDA COM SUCESSO")) {
                        ouvinte.aoAtualizar(
                                "Arquivo compilado. Verificando se o objetivo foi atendido.");
                    }
                    continue;
                }

                ouvinte.aoFalhar("Ação não reconhecida: " + acao);
                return;
            }

            ouvinte.aoConcluir("Limite de ciclos atingido.\n"
                    + "Último resultado da compilação:\n"
                    + feedbackCompilacao);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ouvinte.aoFalhar("O desenvolvimento foi interrompido.");
        } catch (Exception ex) {
            ouvinte.aoFalhar("Erro durante o desenvolvimento: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private String lerFontesProjeto(Path raiz) throws IOException {
        Path fonte = raiz.resolve("src");

        if (!Files.isDirectory(fonte)) {
            return "A pasta src ainda não contém arquivos.";
        }

        StringBuilder resultado = new StringBuilder();

        try (Stream<Path> stream = Files.walk(fonte)) {
            java.util.Iterator<Path> iterator = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".java"))
                    .iterator();

            while (iterator.hasNext()) {
                Path arquivo = iterator.next();
                String conteudo = new String(
                        Files.readAllBytes(arquivo), StandardCharsets.UTF_8);

                if (resultado.length() + conteudo.length()
                        > MAX_CARACTERES_FONTE) {
                    resultado.append("\n[Contexto limitado.]");
                    break;
                }

                resultado.append("\n===== ARQUIVO: ")
                        .append(raiz.relativize(arquivo).toString()
                                .replace('\\', '/'))
                        .append(" =====\n")
                        .append(conteudo)
                        .append("\n===== FIM DO ARQUIVO =====\n");
            }
        }

        return resultado.length() == 0
                ? "Nenhum arquivo Java foi criado ainda."
                : resultado.toString();
    }

    private String criarPrompt(String objetivo, String feedback,
            String arquivosProjeto, int ciclo) {

        return "Você é um agente programador Java experiente.\n\n"
                + "OBJETIVO DO USUÁRIO:\n" + objetivo + "\n\n"
                + "CICLO ATUAL: " + ciclo + " de " + MAX_CICLOS + "\n\n"
                + "RESULTADO DA ÚLTIMA COMPILAÇÃO:\n" + feedback + "\n\n"
                + "CÓDIGO ATUAL DO PROJETO:\n" + arquivosProjeto + "\n\n"
                + "REGRAS:\n"
                + "1. Use Java 8 e Swing quando houver interface gráfica.\n"
                + "2. O usuário já autorizou a execução do objetivo descrito.\n"
                + "3. Nunca pergunte se deve criar arquivos necessários ao objetivo.\n"
                + "4. Se precisar criar ou alterar código, use GRAVAR imediatamente.\n"
                + "5. Grave apenas um arquivo Java por ação.\n"
                + "6. Use caminhos relativos, como src/Principal.java.\n"
                + "7. Não use Markdown no conteúdo do arquivo.\n"
                + "8. Analise os arquivos existentes antes de alterá-los.\n"
                + "9. Corrija erros de compilação quando existirem.\n"
                + "10. Só use PERGUNTAR quando faltar uma informação indispensável.\n"
                + "11. Se o objetivo estiver atendido e a compilação tiver sucesso, conclua.\n\n"
                + "FORMATO PARA GRAVAR:\n"
                + "ACAO: GRAVAR\n"
                + "CAMINHO: src/Principal.java\n"
                + "MENSAGEM: descrição breve\n"
                + "CONTEUDO_INICIO\n"
                + "código Java completo\n"
                + "CONTEUDO_FIM\n\n"
                + "FORMATO PARA PERGUNTAR:\n"
                + "ACAO: PERGUNTAR\n"
                + "MENSAGEM: pergunta indispensável\n\n"
                + "FORMATO PARA CONCLUIR:\n"
                + "ACAO: CONCLUIR\n"
                + "MENSAGEM: resumo do resultado\n\n"
                + "Responda com exatamente uma ação por vez. "
                + "Não escreva explicações fora do formato.";
    }

    private String consultarOllama(String prompt)
            throws IOException {

        HttpURLConnection conexao = null;

        try {
            conexao = (HttpURLConnection)
                    new URL(URL_OLLAMA).openConnection();

            conexao.setRequestMethod("POST");
            conexao.setConnectTimeout(10000);
            conexao.setReadTimeout(180000);
            conexao.setDoOutput(true);
            conexao.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8");

            String corpo = "{"
                    + "\"model\":\"" + escaparJson(MODELO) + "\","
                    + "\"stream\":false,"
                    + "\"messages\":[{\"role\":\"user\",\"content\":\""
                    + escaparJson(prompt) + "\"}]}";

            try (OutputStream out = conexao.getOutputStream()) {
                out.write(corpo.getBytes(StandardCharsets.UTF_8));
            }

            int status = conexao.getResponseCode();
            InputStream entrada = status >= 200 && status < 300
                    ? conexao.getInputStream()
                    : conexao.getErrorStream();

            StringBuilder resposta = new StringBuilder();

            if (entrada != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        resposta.append(linha);
                    }
                }
            }

            if (status < 200 || status >= 300) {
                throw new IOException("Erro HTTP " + status + ": "
                        + limitarTexto(resposta.toString(), 3000));
            }

            String conteudo = extrairStringJson(
                    resposta.toString(), "content");

            if (conteudo == null) {
                throw new IOException(
                        "Não foi possível ler a resposta do Ollama.");
            }

            return conteudo;

        } finally {
            if (conexao != null) {
                conexao.disconnect();
            }
        }
    }

    private static String obterCampo(String texto, String nome) {
        Pattern p = Pattern.compile(
                "(?im)^\\s*" + Pattern.quote(nome)
                + "\\s*:\\s*(.*?)\\s*$");
        Matcher m = p.matcher(texto);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String obterBloco(String texto) {
        int i = texto.indexOf("CONTEUDO_INICIO");
        if (i < 0) {
            return null;
        }

        i += "CONTEUDO_INICIO".length();
        int f = texto.indexOf("CONTEUDO_FIM", i);

        if (f < 0) {
            return null;
        }

        String codigo = texto.substring(i, f);

        if (codigo.startsWith("\r\n")) {
            codigo = codigo.substring(2);
        } else if (codigo.startsWith("\n")) {
            codigo = codigo.substring(1);
        }

        return codigo;
    }

    private static String extrairStringJson(String json, String campo) {
        Pattern p = Pattern.compile(
                "\"" + Pattern.quote(campo)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? desescaparJson(m.group(1)) : null;
    }

    private static String escaparJson(String texto) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }

        return sb.toString();
    }

    private static String desescaparJson(String texto) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            if (c != '\\' || i + 1 >= texto.length()) {
                sb.append(c);
                continue;
            }

            char n = texto.charAt(++i);

            switch (n) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 < texto.length()) {
                        String hex = texto.substring(i + 1, i + 5);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            sb.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        sb.append("\\u");
                    }
                    break;
                default: sb.append(n);
            }
        }

        return sb.toString();
    }

    private static String limitarTexto(String texto, int limite) {
        if (texto == null || texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite) + "\n[Texto truncado]";
    }
}
