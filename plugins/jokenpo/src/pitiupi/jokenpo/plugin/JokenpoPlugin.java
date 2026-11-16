package pitiupi.jokenpo.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.jokenpo.logica.JokenpoControle;
import pitiupi.jokenpo.rede.JokenpoMensagem;
import pitiupi.jokenpo.tela.JokenpoPainel;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

import javax.swing.SwingUtilities;
import java.util.List;

// Liga o Jokenpô ao núcleo do Pitiupi. O núcleo acha esta classe pelo META-INF/services,
// chama createPlugin uma vez e depois receiveMessage pra cada JokenpoMensagem que chegar.
// O plugin não abre socket, quem envia e recebe é o núcleo.
public class JokenpoPlugin extends Plugin {

    private ConnectionManager conexao;
    private PluginWorkspace workspace;
    private JokenpoControle controle;
    private JokenpoPainel painel;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.conexao = connectionManager;
        this.workspace = workspace;

        String meuId = connectionManager.getHeartbeatManager().getInstanceId();
        controle = new JokenpoControle(meuId, connectionManager::getUserName, this::enviar);
        painel = new JokenpoPainel(controle, connectionManager);
        controle.setOuvinte(new JokenpoControle.Ouvinte() {
            @Override
            public void atualizar() {
                painel.atualizar();
            }

            @Override
            public void registrarNoLog(String linha) {
                painel.adicionarLog(linha);
            }

            @Override
            public void chamarAtencao(String texto) {
                abrirAba();
                workspace.setStatusMessage(texto);
            }
        });

        bindShellWorkspaceContent(painel);
        installPeerActions(workspace.peerActions());

        // atualiza a lista de adversários quando alguém entra ou sai da sala
        connectionManager.addPeerListener(peers -> SwingUtilities.invokeLater(painel::atualizarListaDePeers));
    }

    // opção "Desafiar para Jokenpô" ao clicar num nome da lista lateral
    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "jokenpo.desafiar",
                "Desafiar para Jokenpô",
                PeerActionTarget.SINGLE_PEER,
                alvo -> {
                    abrirAba();
                    if (alvo instanceof PeerTarget.Single unico) {
                        controle.convidar(unico.peer().getInstanceId(), unico.peer().getDisplayName());
                    }
                }
        ));
    }

    // tudo vai por UDP multicast, o mesmo caminho do heartbeat: se o peer aparece na lista,
    // a mensagem chega. Quem não é o destinatário ignora.
    private void enviar(JokenpoMensagem mensagem) {
        MessageDispatcher.send(conexao, mensagem, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(JokenpoMensagem.class);
    }

    // chega na thread do socket, o invokeLater passa pra thread do Swing
    @Override
    public void receiveMessage(Message message) {
        if (message instanceof JokenpoMensagem mensagem) {
            SwingUtilities.invokeLater(() -> controle.receber(mensagem));
        }
    }

    private void abrirAba() {
        workspace.openOrFocusTab(getIdentifier(), getDisplayName());
    }

    @Override
    public String getName() {
        return "Jokenpo";
    }

    @Override
    public String getDisplayName() {
        return "Jokenpô";
    }

    @Override
    public String getAuthor() {
        return "Lucas Rivetti";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getIdentifier() {
        return "pitiupi.jokenpo";
    }
}
