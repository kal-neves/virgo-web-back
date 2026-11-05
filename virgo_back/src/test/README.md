# Testes do backend

Testes com JUnit Jupiter e suporte do Spring Boot, organizados conforme os pacotes
de produção. A suíte local usa dados sintéticos e não acessa o PJe.

## Organização

| Pacote | Cobertura |
| --- | --- |
| `consulta/domain` | Estados, resultados e regras de exibição. |
| `consulta/application` e `consulta/execution` | Consultas, execução sequencial e cancelamento com gateway simulado. |
| `consulta/persistence` | Migrações e persistência de rodadas, itens e tentativas. |
| `processo` | Números CNJ, processos monitorados e sua persistência. |
| `movimentacao` | Detecção de novidades e registro do histórico. |
| `identidade` | Identificação do usuário e tratamento de credenciais. |
| `pje` | XML, cliente SOAP e transporte; integração real habilitada separadamente. |

`VirgoBackApplicationTests` verifica o carregamento do contexto Spring.
`PjeTransportTest` usa um servidor HTTP local, sem consultar o PJe.

## Ambiente

- Use JDK 25 e o Maven Wrapper incluído no módulo `virgo_back`.
- O Maven precisa obter as dependências, incluindo os binários do PostgreSQL de teste.
- Os testes de persistência e contexto iniciam um PostgreSQL temporário em porta
  livre via `embedded-postgres`. Não exigem Docker, banco previamente criado nem
  variáveis `SPRING_DATASOURCE_*`; não utilizam o banco da aplicação.
- O banco é encerrado com o contexto. Os testes isolam seus dados por rollback ou,
  quando precisam confirmar transações, por limpeza explícita.

## Executar testes locais

Execute no diretório que contém `pom.xml` e `mvnw.cmd`:

```powershell
.\mvnw.cmd '-DexcludedGroups=pje-live' test
```

Após mover ou renomear testes, use `clean test` para eliminar classes compiladas antigas:

```powershell
.\mvnw.cmd '-DexcludedGroups=pje-live' clean test
```

## Integração real com o PJe

O teste `PjeSoapClientIntegrationTest` é opcional e consulta o PJe real.
Execute somente em ambiente autorizado, com acesso de rede ao endpoint escolhido
e um processo acessível que tenha movimentações.

Defina as variáveis no ambiente da execução:

| Variável | Valor |
| --- | --- |
| `PJE_LIVE_TEST` | `true` para habilitar o teste. |
| `TESTE_LOGIN` | CPF/idConsultante do usuário. |
| `TESTE_SENHA` | Senha do PJe. |
| `TESTE_PROCESSO` | Número CNJ do processo consultado. |
| `TESTE_ORIGEM` | Opcional: `TRF1PJE1` (padrão), `TRF1PJE2`, `TRF6PJE1` ou `TRF6PJE2`. Os aliases `PJE1` e `PJE2` também são aceitos. |

Sem as quatro variáveis obrigatórias, o teste é ignorado.

```powershell
.\mvnw.cmd '-Dtest=PjeSoapClientIntegrationTest' test
```

## Criar e manter testes

- Verifique comportamentos observáveis e contratos; evite depender de métodos
  privados, formatação exata do XML ou texto exato dos erros internos do VIRGO.
- Use fixtures sintéticas. Consulte [as orientações das fixtures](resources/pje/README.md)
  antes de alterar os exemplos XML.
- Não registre credenciais nem respostas reais nas fixtures ou nos logs.
  Mantenha o tracing de mensagens SOAP e de tráfego HTTP desabilitado com credenciais reais.
- Preserve o isolamento dos dados e a limpeza explícita nos testes que confirmam transações.
- Ao alterar contratos, revise os casos de aceitação correspondentes. Mantenha
  integrações reais identificadas com a tag `pje-live` e habilitação explícita.
