package pitiupi.jokenpo.logica;

import pitiupi.jokenpo.rede.JokenpoMensagem;
import pitiupi.jokenpo.rede.JokenpoMensagem.Tipo;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Lógica do jogo e do protocolo. Não usa Swing nem socket: recebe mensagens em receber()
// e manda pelo enviador. Por isso dá pra testar sem rede.
// Tudo roda na thread do Swing, então não precisa de synchronized.
public class JokenpoControle {

    // melhor de 3, empate não conta
    public static final int VITORIAS_PARA_GANHAR = 2;

    public enum Fase {
        LIVRE,
        CONVITE_ENVIADO,
        CONVITE_RECEBIDO,
        JOGANDO,
        FIM
    }

    // a tela implementa isso pra saber quando se atualizar
    public interface Ouvinte {
        void atualizar();

        void registrarNoLog(String linha);

        // algo importante (ex.: convite recebido): abre a aba e avisa
        void chamarAtencao(String texto);
    }

    private final String meuId;
    private final Supplier<String> meuNome; // Supplier porque o nome pode mudar nas Configurações
    private final Consumer<JokenpoMensagem> enviador;
    private Ouvinte ouvinte;

    // partida
    private Fase fase = Fase.LIVRE;
    private String partidaId;
    private String adversarioId;
    private String adversarioNome;
    private int rodada;
    private int minhasVitorias;
    private int vitoriasAdversario;
    private String aviso = "Escolha alguém da sala e clique em Desafiar.";
    private String resultadoUltimaRodada = "";

    // rodada atual
    private Jogada minhaJogada;
    private Jogada jogadaAdversario;

    public JokenpoControle(String meuId, Supplier<String> meuNome, Consumer<JokenpoMensagem> enviador) {
        this.meuId = meuId;
        this.meuNome = meuNome;
        this.enviador = enviador;
    }

    public void setOuvinte(Ouvinte ouvinte) {
        this.ouvinte = ouvinte;
    }

    // ---------- ações do jogador (botões da tela) ----------

    public void convidar(String peerId, String peerNome) {
        if (!podeConvidar()) {
            return;
        }
        novaPartida(UUID.randomUUID().toString(), peerId, peerNome);
        fase = Fase.CONVITE_ENVIADO;
        aviso = "Convite enviado para " + peerNome + ". Aguardando resposta...";
        enviar(nova(Tipo.CONVITE));
        ouvinte.atualizar();
    }

    public void aceitarConvite() {
        if (fase != Fase.CONVITE_RECEBIDO) {
            return;
        }
        enviar(nova(Tipo.ACEITO));
        fase = Fase.JOGANDO;
        iniciarRodada(1);
        ouvinte.atualizar();
    }

    public void recusarConvite() {
        if (fase != Fase.CONVITE_RECEBIDO) {
            return;
        }
        enviar(nova(Tipo.RECUSADO, "não quis jogar agora"));
        fase = Fase.FIM;
        aviso = "Você recusou o convite de " + adversarioNome + ".";
        ouvinte.atualizar();
    }

    // cancela o convite enviado ou desiste da partida
    public void desistir() {
        if (fase == Fase.CONVITE_ENVIADO) {
            encerrar("cancelou o convite", "Convite cancelado.");
        } else if (fase == Fase.JOGANDO) {
            encerrar("desistiu da partida", "Você desistiu. " + adversarioNome + " venceu por W.O.");
        }
    }

    public void jogar(Jogada jogada) {
        if (!podeJogar()) {
            return;
        }
        minhaJogada = jogada;
        JokenpoMensagem m = nova(Tipo.JOGADA);
        m.jogada = jogada;
        enviar(m);
        aviso = "Você escolheu " + jogada + ". Aguardando " + adversarioNome + "...";
        if (jogadaAdversario != null) {
            fecharRodada();
        }
        ouvinte.atualizar();
    }

    // ---------- mensagens que chegam da rede ----------

    public void receber(JokenpoMensagem m) {
        if (!meuId.equals(m.para)) {
            return; // é pra outra pessoa (multicast entrega pra sala toda)
        }
        if (m.tipo == Tipo.CONVITE) {
            receberConvite(m);
        } else if (m.partidaId.equals(partidaId) && m.de.equals(adversarioId)) {
            ouvinte.registrarNoLog("<- " + adversarioNome + ": " + m);
            switch (m.tipo) {
                case ACEITO:
                    if (fase == Fase.CONVITE_ENVIADO) {
                        fase = Fase.JOGANDO;
                        iniciarRodada(1);
                    }
                    break;
                case RECUSADO:
                    if (fase == Fase.CONVITE_ENVIADO) {
                        fase = Fase.FIM;
                        aviso = adversarioNome + " recusou o convite (" + m.texto + ").";
                    }
                    break;
                case ENCERRAR:
                    if (estaEmPartida()) {
                        fase = Fase.FIM;
                        aviso = adversarioNome + " " + m.texto + ".";
                    }
                    break;
                case JOGADA:
                    receberJogada(m);
                    break;
            }
        }
        ouvinte.atualizar();
    }

    private void receberConvite(JokenpoMensagem m) {
        if (m.partidaId.equals(partidaId)) {
            return; // convite repetido (o UDP pode entregar o mesmo pacote duas vezes)
        }
        if (estaEmPartida()) {
            // já estou jogando: recuso sem mexer na partida atual
            JokenpoMensagem ocupado = new JokenpoMensagem(
                    Tipo.RECUSADO, m.partidaId, meuId, meuNome.get(), m.de);
            ocupado.texto = "está ocupado em outra partida";
            enviador.accept(ocupado);
            return;
        }
        ouvinte.registrarNoLog("<- " + m.deNome + ": " + m);
        novaPartida(m.partidaId, m.de, m.deNome);
        fase = Fase.CONVITE_RECEBIDO;
        aviso = adversarioNome + " desafiou você para uma partida (melhor de 3).";
        ouvinte.chamarAtencao("Jokenpô: " + aviso);
    }

    private void receberJogada(JokenpoMensagem m) {
        if (fase != Fase.JOGANDO || m.rodada != rodada || m.jogada == null) {
            return; // de outra rodada, ou sem jogada
        }
        jogadaAdversario = m.jogada;
        if (minhaJogada == null) {
            // não mostro qual foi, senão eu poderia escolher a que ganha
            aviso = adversarioNome + " já jogou. Escolha a sua jogada.";
        } else {
            fecharRodada();
        }
    }

    // ---------- fim da rodada ----------

    // só é chamado quando tenho as duas jogadas
    private void fecharRodada() {
        Jogada dele = jogadaAdversario;
        String quem;
        if (minhaJogada.ganhaDe(dele)) {
            minhasVitorias++;
            quem = "Você venceu a rodada";
        } else if (dele.ganhaDe(minhaJogada)) {
            vitoriasAdversario++;
            quem = adversarioNome + " venceu a rodada";
        } else {
            quem = "Empate, não conta";
        }
        resultadoUltimaRodada = "Rodada " + rodada + ": você jogou " + minhaJogada + ", "
                + adversarioNome + " jogou " + dele + ". " + quem + ".";
        ouvinte.registrarNoLog(resultadoUltimaRodada);

        if (minhasVitorias == VITORIAS_PARA_GANHAR) {
            fase = Fase.FIM;
            aviso = "Você venceu a partida!";
        } else if (vitoriasAdversario == VITORIAS_PARA_GANHAR) {
            fase = Fase.FIM;
            aviso = adversarioNome + " venceu a partida.";
        } else {
            iniciarRodada(rodada + 1);
        }
    }

    // ---------- auxiliares ----------

    private void novaPartida(String id, String peerId, String peerNome) {
        partidaId = id;
        adversarioId = peerId;
        adversarioNome = peerNome;
        rodada = 0;
        minhasVitorias = 0;
        vitoriasAdversario = 0;
        resultadoUltimaRodada = "";
    }

    private void iniciarRodada(int numero) {
        rodada = numero;
        minhaJogada = null;
        jogadaAdversario = null;
        aviso = "Rodada " + numero + ": escolha sua jogada.";
    }

    private void encerrar(String textoParaAdversario, String avisoParaMim) {
        enviar(nova(Tipo.ENCERRAR, textoParaAdversario));
        fase = Fase.FIM;
        aviso = avisoParaMim;
        ouvinte.atualizar();
    }

    // mensagem pro adversário, já com a partida e a rodada atual
    private JokenpoMensagem nova(Tipo tipo) {
        JokenpoMensagem m = new JokenpoMensagem(tipo, partidaId, meuId, meuNome.get(), adversarioId);
        m.rodada = rodada;
        return m;
    }

    private JokenpoMensagem nova(Tipo tipo, String texto) {
        JokenpoMensagem m = nova(tipo);
        m.texto = texto;
        return m;
    }

    private void enviar(JokenpoMensagem m) {
        ouvinte.registrarNoLog("-> " + m);
        enviador.accept(m);
    }

    // ---------- usados pela tela ----------

    public boolean podeConvidar() {
        return fase == Fase.LIVRE || fase == Fase.FIM;
    }

    public boolean podeJogar() {
        return fase == Fase.JOGANDO && minhaJogada == null;
    }

    public boolean estaEmPartida() {
        return fase == Fase.CONVITE_ENVIADO || fase == Fase.CONVITE_RECEBIDO || fase == Fase.JOGANDO;
    }

    public Fase getFase() {
        return fase;
    }

    public String getAviso() {
        return aviso;
    }

    public String getResultadoUltimaRodada() {
        return resultadoUltimaRodada;
    }

    public String getAdversarioNome() {
        return adversarioNome;
    }

    public int getRodada() {
        return rodada;
    }

    public int getMinhasVitorias() {
        return minhasVitorias;
    }

    public int getVitoriasAdversario() {
        return vitoriasAdversario;
    }
}
