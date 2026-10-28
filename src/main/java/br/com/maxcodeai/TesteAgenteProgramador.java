
package br.com.maxcodeai;

import java.nio.file.Path;

public class TesteAgenteProgramador {

    public static void main(String[] args) {

        try {
            Path projeto = AgenteProgramador.criarProjeto(
                    "MeuPrimeiroProjeto"
            );

            String codigo =
                    "public class Principal {\n"
                    + "    public static void main(String[] args) {\n"
                    + "        System.out.println(\"Meu programa foi criado pelo MaxcodeAI!\");\n"
                    + "    }\n"
                    + "}\n";

            AgenteProgramador.gravarArquivo(
                    projeto,
                    "src/Principal.java",
                    codigo
            );

            System.out.println("Projeto criado em: " + projeto);
            System.out.println(AgenteProgramador.compilar(projeto));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
