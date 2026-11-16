package pitiupi.jokenpo.logica;

import org.junit.jupiter.api.Test;
import pitiupi.jokenpo.logica.JokenpoControle.Fase;
import pitiupi.jokenpo.rede.JokenpoMensagem;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Testa o protocolo sem rede de verdade. A RedeFalsa entrega cada mensagem pra todos os
// jogadores (como o multicast), serializando igual o núcleo faz.
class JokenpoControleTest {

    // ouvinte que não faz nada, a tela não importa aqui
    static final JokenpoControle.Ouvinte SEM_TELA = new JokenpoControle.Ouvinte() {
        @Override
        public void atualizar() {
        }

        @Override
        public void registrarNoLog(String linha) {
        }

        @Override
        public void chamarAtencao(String texto) {
        }
    };

    static class RedeFalsa {
        final List<JokenpoControle> jogadores = new ArrayList<>();
        final Deque<byte[]> fila = new ArrayDeque<>();

        JokenpoControle novoJogador(String id, String nome) {
            JokenpoControle c = new JokenpoControle(id, () -> nome, m -> {
                try {
                    fila.add(m.toByteArray());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            c.setOuvinte(SEM_TELA);
            jogadores.add(c);
            return c;
        }

        // entrega a fila toda, inclusive as respostas que forem sendo geradas
        void entregar() {
            while (!fila.isEmpty()) {
                byte[] bytes = fila.poll();
                for (JokenpoControle c : jogadores) {
                    c.receber(desserializar(bytes));
                }
            }
        }

        static JokenpoMensagem desserializar(byte[] bytes) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (JokenpoMensagem) in.readObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void regrasDoJogo() {
        assertTrue(Jogada.PEDRA.ganhaDe(Jogada.TESOURA));
        assertTrue(Jogada.PAPEL.ganhaDe(Jogada.PEDRA));
        assertTrue(Jogada.TESOURA.ganhaDe(Jogada.PAPEL));
        assertFalse(Jogada.PEDRA.ganhaDe(Jogada.PEDRA));
        assertFalse(Jogada.TESOURA.ganhaDe(Jogada.PEDRA));
    }

    @Test
    void partidaCompletaComEmpateNoMeio() {
        RedeFalsa rede = new RedeFalsa();
        JokenpoControle ana = rede.novoJogador("A", "Ana");
        JokenpoControle bruno = rede.novoJogador("B", "Bruno");

        ana.convidar("B", "Bruno");
        rede.entregar();
        assertEquals(Fase.CONVITE_RECEBIDO, bruno.getFase());

        bruno.aceitarConvite();
        rede.entregar();
        assertEquals(Fase.JOGANDO, ana.getFase());

        // rodada 1: empate, não conta
        ana.jogar(Jogada.PEDRA);
        rede.entregar();
        assertEquals(1, bruno.getRodada(), "Bruno ainda não jogou");
        bruno.jogar(Jogada.PEDRA);
        rede.entregar();
        assertEquals(2, ana.getRodada());
        assertEquals(0, ana.getMinhasVitorias());

        // rodadas 2 e 3: Ana ganha com pedra contra tesoura
        for (int i = 0; i < 2; i++) {
            bruno.jogar(Jogada.TESOURA);
            ana.jogar(Jogada.PEDRA);
            rede.entregar();
        }

        assertEquals(Fase.FIM, ana.getFase());
        assertEquals(Fase.FIM, bruno.getFase());
        assertEquals(2, ana.getMinhasVitorias());
        assertEquals(2, bruno.getVitoriasAdversario());
        assertEquals("Você venceu a partida!", ana.getAviso());
        assertEquals("Ana venceu a partida.", bruno.getAviso());
    }

    @Test
    void terceiroPeerIgnoraAsMensagensDosOutros() {
        RedeFalsa rede = new RedeFalsa();
        JokenpoControle ana = rede.novoJogador("A", "Ana");
        JokenpoControle bruno = rede.novoJogador("B", "Bruno");
        JokenpoControle carla = rede.novoJogador("C", "Carla");

        ana.convidar("B", "Bruno");
        rede.entregar();
        bruno.aceitarConvite();
        rede.entregar();
        bruno.jogar(Jogada.PAPEL);
        ana.jogar(Jogada.PEDRA);
        rede.entregar();

        assertEquals(1, bruno.getMinhasVitorias());
        assertEquals(Fase.LIVRE, carla.getFase(), "Carla recebeu tudo pelo multicast, mas não era pra ela");
    }

    @Test
    void jogadaDoAdversarioFicaEscondidaAteEuJogar() {
        RedeFalsa rede = new RedeFalsa();
        JokenpoControle ana = rede.novoJogador("A", "Ana");
        JokenpoControle bruno = rede.novoJogador("B", "Bruno");
        ana.convidar("B", "Bruno");
        rede.entregar();
        bruno.aceitarConvite();
        rede.entregar();

        bruno.jogar(Jogada.TESOURA);
        rede.entregar();

        // a jogada do Bruno já chegou, mas a tela da Ana não pode mostrar qual foi
        assertEquals("Bruno já jogou. Escolha a sua jogada.", ana.getAviso());
        assertEquals("", ana.getResultadoUltimaRodada());

        ana.jogar(Jogada.PEDRA);
        rede.entregar();
        assertTrue(ana.getResultadoUltimaRodada().contains("Bruno jogou TESOURA"));
    }

    @Test
    void conviteDuranteUmaPartidaEhRecusado() {
        RedeFalsa rede = new RedeFalsa();
        JokenpoControle ana = rede.novoJogador("A", "Ana");
        JokenpoControle bruno = rede.novoJogador("B", "Bruno");
        JokenpoControle carla = rede.novoJogador("C", "Carla");
        ana.convidar("B", "Bruno");
        rede.entregar();
        bruno.aceitarConvite();
        rede.entregar();

        carla.convidar("B", "Bruno");
        rede.entregar();

        assertEquals(Fase.FIM, carla.getFase());
        assertTrue(carla.getAviso().contains("ocupado"));
        assertEquals(Fase.JOGANDO, bruno.getFase(), "a partida com a Ana continua");
    }

    @Test
    void desistenciaEncerraParaOsDois() {
        RedeFalsa rede = new RedeFalsa();
        JokenpoControle ana = rede.novoJogador("A", "Ana");
        JokenpoControle bruno = rede.novoJogador("B", "Bruno");
        ana.convidar("B", "Bruno");
        rede.entregar();
        bruno.aceitarConvite();
        rede.entregar();

        bruno.desistir();
        rede.entregar();

        assertEquals(Fase.FIM, ana.getFase());
        assertEquals("Bruno desistiu da partida.", ana.getAviso());
    }
}
