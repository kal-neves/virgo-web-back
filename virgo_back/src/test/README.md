# Testes do backend

Os pacotes espelham o código de produção:

- `consulta/domain`: janela e exibição por data capturada, sem relógio do sistema.
- `consulta/application`: consulta aos dois graus numa nova consulta, com gateway local.
- `processo/domain`: normalização e rejeição de números CNJ.
- `processo/persistence`: migração Flyway e repository JDBC contra PostgreSQL temporário.
- `pje`: writer, mapper, transporte simulado e integração real opt-in.
- `PjeTransportTest`: transporte HTTP/SOAP real contra servidor loopback, sem chamadas ao PJe.
- `VirgoBackApplicationTests`: carregamento do contexto Spring.

Os testes de persistência e contexto iniciam PostgreSQL local temporário em porta
livre via `embedded-postgres`, sem Docker e sem usar o banco da aplicação. O Maven
obtém os binários como dependências de teste; o contexto encerra o banco ao fechar.
As alterações de cada teste de repository são revertidas por transação.

Os testes locais verificam resultados observáveis: campos XML, fronteiras de data,
classificação de falhas e preservação dos resultados de cada grau. Não verificam
métodos privados, formatação exata do XML ou texto exato dos erros produzidos pelo
VIRGO. As fixtures XML são sintéticas; sua origem está em `resources/pje/README.md`.

O transporte usa `pje.call-timeout` como prazo desde a criação da conexão Spring-WS
até o recebimento completo do corpo HTTP, incluindo DNS e espera por conexão.
`connect-timeout` e `read-timeout` continuam sendo limites independentes, que podem
encerrar a chamada antes. A transformação local do XML e o mapeamento após o
recebimento não são interrompidos por esse prazo de rede.
O cancelamento atinge a troca HTTP real; não apenas uma thread de espera. DNS nativo
pode ignorar interrupção, por isso suas threads/fila são limitadas e nunca enviam HTTP.
É usado o primeiro endereço resolvido, sem tentativa automática em outro endereço.
O hostname permanece na validação TLS e no cabeçalho Host. Retries, redirects e
armazenamento de cookies estão desabilitados; o Spring fecha o cliente no shutdown.
As respostas SOAP 1.1 são recebidas em memória e analisadas com DTD, entidades
externas e XInclude desabilitados. Multipart/attachments não são suportados por
esse caminho de consulta de movimentos. Não habilite tracing de mensagens/wire
em ambientes com credenciais reais.

Execute a suíte local a partir do módulo `virgo_back`:

```powershell
.\mvnw.cmd '-DexcludedGroups=pje-live' test
```

Se houver classes compiladas antigas após mover/renomear testes, execute `clean test`
com a mesma exclusão. A integração real está em `pje/PjeSoapClientIntegrationTest`
e exige **todas** as variáveis `PJE_LIVE_TEST=true`, `TESTE_LOGIN`, `TESTE_SENHA`
e `TESTE_PROCESSO`. `TESTE_ORIGEM` aceita `TRF1PJE1` (padrão), `TRF1PJE2`, `TRF6PJE1` ou `TRF6PJE2`;
os aliases anteriores `PJE1` e `PJE2` continuam aceitos.

Com essas variáveis definidas no ambiente autorizado:

```powershell
.\mvnw.cmd '-Dtest=PjeSoapClientIntegrationTest' test
```

Esse teste faz uma consulta real à origem escolhida. Use um processo acessível com
movimentações; uma lista vazia pode ser válida no produto, mas não exercita o
mapeamento desse teste. Não grave credenciais nem respostas reais nas fixtures.

Mudanças de pacote exigem ajustar imports; mudanças de contrato exigem atualizar
casos de aceitação. Estes testes evitam detalhes internos, mas não são imunes a
evoluções legítimas do produto. A persistência atual cobre identidade mínima e
processos monitorados. Continuação de rodadas, histórico de detecções e descoberta
de tribunal ainda não estão implementados.
