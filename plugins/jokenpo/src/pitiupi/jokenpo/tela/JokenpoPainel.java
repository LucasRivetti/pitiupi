package pitiupi.jokenpo.tela;

import pitiupi.application.presence.UserPeerView;
import pitiupi.control.ConnectionManager;
import pitiupi.jokenpo.logica.Jogada;
import pitiupi.jokenpo.logica.JokenpoControle;
import pitiupi.jokenpo.logica.JokenpoControle.Fase;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// Tela do plugin. Só mostra o estado do controle e repassa os cliques pra ele.
public class JokenpoPainel extends JPanel {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // item do combo: guarda o id, mas mostra só o nome
    private record Peer(String id, String nome) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private final JokenpoControle controle;
    private final ConnectionManager conexao;

    private final JComboBox<Peer> listaPeers = new JComboBox<>();
    private final JButton botaoDesafiar = new JButton("Desafiar");
    private final JLabel labelAviso = new JLabel();
    private final JLabel labelPlacar = new JLabel(" ");
    private final JLabel labelUltimaRodada = new JLabel(" ");
    private final JButton botaoAceitar = new JButton("Aceitar");
    private final JButton botaoRecusar = new JButton("Recusar");
    private final JButton botaoPedra = new JButton("Pedra");
    private final JButton botaoPapel = new JButton("Papel");
    private final JButton botaoTesoura = new JButton("Tesoura");
    private final JButton botaoDesistir = new JButton("Desistir");
    private final JTextArea textoLog = new JTextArea();

    public JokenpoPainel(JokenpoControle controle, ConnectionManager conexao) {
        this.controle = controle;
        this.conexao = conexao;
        montarTela();

        botaoDesafiar.addActionListener(e -> {
            Peer escolhido = (Peer) listaPeers.getSelectedItem();
            if (escolhido != null) {
                controle.convidar(escolhido.id(), escolhido.nome());
            }
        });
        botaoAceitar.addActionListener(e -> controle.aceitarConvite());
        botaoRecusar.addActionListener(e -> controle.recusarConvite());
        botaoPedra.addActionListener(e -> controle.jogar(Jogada.PEDRA));
        botaoPapel.addActionListener(e -> controle.jogar(Jogada.PAPEL));
        botaoTesoura.addActionListener(e -> controle.jogar(Jogada.TESOURA));
        botaoDesistir.addActionListener(e -> controle.desistir());

        atualizarListaDePeers();
    }

    private void montarTela() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topo.add(new JLabel("Adversário:"));
        listaPeers.setPrototypeDisplayValue(new Peer("", "Nome bem comprido aqui")); // só pra dar largura ao combo
        topo.add(listaPeers);
        topo.add(botaoDesafiar);

        labelAviso.setFont(labelAviso.getFont().deriveFont(Font.BOLD, 16f));
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton botao : new JButton[]{botaoAceitar, botaoRecusar, botaoPedra, botaoPapel, botaoTesoura, botaoDesistir}) {
            botoes.add(botao);
        }

        JPanel centro = new JPanel(new GridLayout(0, 1)); // uma coluna, um item por linha
        centro.add(topo);
        centro.add(labelAviso);
        centro.add(labelPlacar);
        centro.add(labelUltimaRodada);
        centro.add(botoes);
        add(centro, BorderLayout.NORTH);

        textoLog.setEditable(false);
        textoLog.setLineWrap(true);
        textoLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane log = new JScrollPane(textoLog);
        log.setBorder(BorderFactory.createTitledBorder("Log do protocolo (-> enviei, <- recebi)"));
        log.setPreferredSize(new Dimension(600, 260));
        add(log, BorderLayout.CENTER);
    }

    // redesenha a tela a partir do estado do controle (sempre na thread do Swing)
    public void atualizar() {
        Fase fase = controle.getFase();
        labelAviso.setText(controle.getAviso());
        if (controle.getRodada() > 0) {
            labelPlacar.setText("Você " + controle.getMinhasVitorias() + " x " + controle.getVitoriasAdversario()
                    + " " + controle.getAdversarioNome() + "   (melhor de 3, rodada " + controle.getRodada() + ")");
        }
        labelUltimaRodada.setText(controle.getResultadoUltimaRodada() + " ");

        botaoDesafiar.setEnabled(controle.podeConvidar() && listaPeers.getItemCount() > 0);
        botaoAceitar.setVisible(fase == Fase.CONVITE_RECEBIDO);
        botaoRecusar.setVisible(fase == Fase.CONVITE_RECEBIDO);
        botaoPedra.setEnabled(controle.podeJogar());
        botaoPapel.setEnabled(controle.podeJogar());
        botaoTesoura.setEnabled(controle.podeJogar());
        botaoDesistir.setText(fase == Fase.CONVITE_ENVIADO ? "Cancelar convite" : "Desistir");
        botaoDesistir.setEnabled(fase == Fase.CONVITE_ENVIADO || fase == Fase.JOGANDO);
    }

    // quem está na sala vem da presença do núcleo (heartbeat); tiro eu mesmo e o "Todos"
    public void atualizarListaDePeers() {
        listaPeers.removeAllItems();
        for (UserPeerView view : conexao.getPresenceService().listPeerViews()) {
            if (!view.isSelf() && !view.isBroadcastTarget()) {
                listaPeers.addItem(new Peer(view.getInstanceId(), view.getDisplayName()));
            }
        }
        atualizar();
    }

    public void adicionarLog(String linha) {
        textoLog.append(LocalTime.now().format(HORA) + "  " + linha + "\n");
        textoLog.setCaretPosition(textoLog.getDocument().getLength());
    }
}
