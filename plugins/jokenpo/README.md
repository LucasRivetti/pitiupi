# Jokenpô

Plugin de pedra, papel e tesoura pro Pitiupi, feito pro trabalho de Redes de Computadores.

Duas pessoas da mesma sala jogam uma partida melhor de 3. Ganha quem vencer 2 rodadas primeiro, e empate não conta.

Só mexi na pasta `plugins/jokenpo` e adicionei a entrada do plugin no `plugins.registry.json`. O núcleo e os outros plugins continuam iguais.

## Como usar

- Abre o Jokenpô na aba Plugins (ou clica no nome de alguém na lista da direita e escolhe "Desafiar para Jokenpô").
- Escolhe o adversário e clica em Desafiar.
- Do outro lado a aba abre sozinha com os botões Aceitar e Recusar.
- Em cada rodada os dois clicam em Pedra, Papel ou Tesoura.
- Desistir encerra a partida. Se o outro ainda não respondeu o convite, o botão vira Cancelar convite.

Embaixo da tela tem um log com as mensagens enviadas (`->`) e recebidas (`<-`), dá pra acompanhar o protocolo por ali.

Os dois precisam estar na mesma porta e com a mesma versão do plugin.

## Como rodar

Precisa de Java 17 ou mais novo e do Ant. Como o plugin já está no `plugins.registry.json`, é só rodar na raiz do projeto:

```
scripts\pitiupi.cmd run --all      (Windows)
./scripts/pitiupi.sh run --all     (Linux / WSL)
```

Pra testar precisa de dois computadores na mesma rede e na mesma porta. Duas janelas no mesmo PC não se enxergam (isso tá explicado no `docs/usar.md`).

Os testes rodam com `ant test` dentro de `plugins/jokenpo` (precisa do JUnit em `pitiupi/test/lib`).

## Protocolo

### Como as mensagens são enviadas

O plugin não abre nenhum socket. Ele só monta a mensagem e pede pro núcleo enviar:

```java
MessageDispatcher.send(conexao, mensagem, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
```

Usei UDP multicast porque é o mesmo caminho que o núcleo usa pro heartbeat. Então se a pessoa aparece na lista de usuários, a mensagem chega nela. O grupo é o `224.0.0.3`, na porta escolhida no login.

Como o multicast manda pra sala inteira, cada mensagem tem um campo `para` com o id de quem deve receber, e quem não for o destinatário ignora. Esse id é o mesmo `instanceId` que o núcleo usa no heartbeat.

O núcleo transforma a mensagem em bytes usando a serialização do próprio Java (`Message` é `Serializable`). Por isso os dois lados precisam ter a mesma versão da classe.

### A mensagem

Tem uma classe só, a `JokenpoMensagem`, com estes campos:

- `tipo`: o que a mensagem é (CONVITE, ACEITO, RECUSADO, JOGADA ou ENCERRAR)
- `partidaId`: um UUID que quem convida cria, pra identificar a partida
- `de` e `deNome`: id e nome de quem mandou
- `para`: id de quem deve receber
- `rodada`: número da rodada (só na JOGADA)
- `jogada`: PEDRA, PAPEL ou TESOURA (só na JOGADA)
- `texto`: o motivo (só no RECUSADO e no ENCERRAR)

### Tipos de mensagem

| Tipo | Quando é enviada |
|---|---|
| CONVITE | alguém clicou em Desafiar |
| ACEITO | o convidado aceitou |
| RECUSADO | o convidado recusou, ou já estava em outra partida (aí recusa sozinho) |
| JOGADA | cada jogador manda a sua em cada rodada |
| ENCERRAR | alguém desistiu ou cancelou o convite |

### Como uma partida acontece

```
Ana                                    Bruno
 |                                       |
 |----------- CONVITE ------------------>|
 |<------------------------- ACEITO -----|
 |                                       |
 |----------- JOGADA (1, PEDRA) -------->|   rodada 1
 |<-------------- JOGADA (1, TESOURA) ---|
 |                                       |
 |    os dois calculam: Ana 1 x 0        |
 |                                       |
 |    ... e assim até alguém fazer 2     |
```

Cada um calcula o resultado sozinho quando tem as duas jogadas, e os dois chegam no mesmo placar. Não importa quem joga primeiro, quem jogou antes só espera a jogada do outro chegar.

Cada jogador sempre está em um destes estados: LIVRE, CONVITE_ENVIADO, CONVITE_RECEBIDO, JOGANDO ou FIM. Quando a partida acaba (alguém fez 2 vitórias, desistiu ou recusou o convite) ele vai pra FIM e já pode desafiar alguém de novo.

### Regras que cada jogador segue

- Se a mensagem não tem o meu id no `para`, ignoro.
- Se é de outra partida ou de outra rodada, também ignoro.
- Se chega um convite enquanto eu já estou jogando, respondo RECUSADO automático e continuo a minha partida.
- Se a jogada do outro chega antes de eu jogar, eu guardo mas não mostro qual foi. A tela só fala "fulano já jogou", senão daria pra escolher a jogada que ganha. As duas jogadas só aparecem juntas no resultado da rodada.

### Limitações

- UDP não garante entrega e o plugin não reenvia nada. Se uma mensagem se perder a partida trava e tem que clicar em Desistir.
- Se o outro fechar o programa no meio da partida, o plugin não percebe sozinho. Também é só desistir.
- A jogada do adversário pode chegar antes da minha e só não aparece porque a tela esconde. Alguém com uma versão modificada do plugin conseguiria ver. Dava pra resolver isso com commit-reveal (cada um manda primeiro só um hash da jogada e depois revela).
- Não tem criptografia e ninguém confere se o `de` é de verdade quem diz ser.

## Código

- `logica/Jogada.java`: as três jogadas e quem ganha de quem
- `logica/JokenpoControle.java`: os estados e as regras do protocolo. Não usa Swing nem rede, então dá pra testar sozinho
- `rede/JokenpoMensagem.java`: a mensagem
- `tela/JokenpoPainel.java`: a tela (botões, placar e log)
- `plugin/JokenpoPlugin.java`: liga tudo no núcleo (envia, recebe e atualiza a lista de usuários)

As mensagens chegam numa thread do núcleo e o plugin passa elas pra thread do Swing com `invokeLater`. Assim tudo roda numa thread só e não precisa de `synchronized`.

Os testes ficam em `test/` e usam uma rede falsa que entrega cada mensagem pra todo mundo, igual o multicast.
