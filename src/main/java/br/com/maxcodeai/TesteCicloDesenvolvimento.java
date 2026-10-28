
package br.com.maxcodeai;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

public class TesteCicloDesenvolvimento {

    public static void main(String[] args) {

        try {
            Path projeto = AgenteProgramador.criarProjeto(
                    "ProjetoCriadoPelaIA"
            );

            CicloDesenvolvimento agente = new CicloDesenvolvimento(
                new CicloDesenvolvimento.Ouvinte() {

                    @Override
                    public void aoAtualizar(String mensagem) {
                        System.out.println("[ANDAMENTO] " + mensagem);
                    }

                    @Override
                    public void aoConcluir(String resultado) {
                        System.out.println("[CONCLUÍDO] " + resultado);
                    }

                    @Override
                    public void aoPerguntar(String pergunta) {
                        System.out.println("[PRECISA DE VOCÊ] " + pergunta);
                    }

                    @Override
                    public void aoFalhar(String erro) {
                        System.err.println("[ERRO] " + erro);
                    }
                }
            );

            Thread trabalho = new Thread(new Runnable() {
                @Override
                public void run() {
                    agente.iniciar(
                        projeto,
                        "Crie uma aplicação Java simples com uma classe "
                        + "Principal que imprima uma mensagem de boas-vindas."
                    );
                }
            });

            trabalho.setName("MaxcodeAI-Agente");
            trabalho.start();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
