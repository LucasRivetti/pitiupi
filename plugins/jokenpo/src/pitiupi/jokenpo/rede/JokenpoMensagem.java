package pitiupi.jokenpo.rede;

import pitiupi.jokenpo.logica.Jogada;
import pitiupi.models.Messages.Message;

// Única mensagem do protocolo, o campo tipo diz o que ela é.
// Vai por multicast, então a sala toda recebe; só processa quem tem o id em "para".
// O núcleo serializa com a serialização do Java, então os dois lados precisam da mesma versão.
public class JokenpoMensagem extends Message {

    private static final long serialVersionUID = 1L;

    public enum Tipo {
        CONVITE,
        ACEITO,
        RECUSADO,  // não quis jogar ou já está ocupado
        JOGADA,
        ENCERRAR   // desistiu ou cancelou o convite
    }

    public final Tipo tipo;
    public final String partidaId; // UUID criado por quem convida
    public final String de;        // id da instância (o mesmo do heartbeat)
    public final String deNome;
    public final String para;      // id do destinatário

    public int rodada;
    public Jogada jogada;  // JOGADA
    public String texto;   // motivo (RECUSADO, ENCERRAR)

    public JokenpoMensagem(Tipo tipo, String partidaId, String de, String deNome, String para) {
        this.tipo = tipo;
        this.partidaId = partidaId;
        this.de = de;
        this.deNome = deNome;
        this.para = para;
    }

    // usado no log da tela
    @Override
    public String toString() {
        switch (tipo) {
            case JOGADA:
                // não mostra a jogada: a do adversário pode chegar antes de eu jogar
                return "JOGADA rodada " + rodada;
            case RECUSADO:
            case ENCERRAR:
                return tipo + " (" + texto + ")";
            default:
                return tipo.toString();
        }
    }
}
