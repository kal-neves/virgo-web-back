# VIRGO Web — Backend

## Descrição

Repositório do backend do VIRGO Web, um sistema para acompanhar movimentações de processos judiciais.

O VIRGO facilita o acompanhamento de processos importantes, identifica novas movimentações e disponibiliza as informações necessárias para a equipe de monitoramento de processos prioritários da AGU/PGF.

---

## Tecnologias usadas

Java 25 com Spring Boot 4.1.1 (Backend)
Spring Web MVC e Bean Validation (API REST e validação de dados)
Spring Web Services e Apache HttpClient 5 (Integração SOAP e requisições HTTP)
PostgreSQL, Spring JDBC e Flyway (Banco de dados, persistência e migrações)
Spring Boot Test e embedded-postgres (Testes)
Lombok e Spring Boot DevTools (Ferramentas de desenvolvimento)

---

## Configuração (WIP)

Configure um banco PostgreSQL vazio para o VIRGO e forneça à aplicação:

- `SPRING_DATASOURCE_URL` (exemplo: `jdbc:postgresql://localhost:5432/virgo`)
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

O Flyway aplica automaticamente as migrações de `src/main/resources/db/migration`
na inicialização. Não edite uma migração já aplicada; acrescente outra versão.
Credenciais de banco são distintas das credenciais PJe, que nunca são persistidas.

A primeira migração cria `usuario` apenas com `id_consultante` (CPF normalizado)
e `processo_monitorado`. Cadastro, perfil e autenticação serão implementados em
outra etapa. O repository exige um proprietário previamente cadastrado; salvar
um processo não cria usuários implicitamente.

`ProcessoMonitoradoRepository.salvar` insere pelo UUID ou atualiza os metadados
e substitui o conjunto de graus observados desse mesmo registro. Proprietário
e CNJ são imutáveis para um UUID existente. A combinação proprietário + CNJ é
única no banco, mas proprietários diferentes podem monitorar o mesmo CNJ.
As buscas exigem proprietário; a futura camada de aplicação deve resolver esse
proprietário após autorizar o usuário, incluindo acesso por compartilhamento.

Os dois graus são armazenados como booleanos, na mesma linha do processo, para
salvar os metadados e o conjunto inteiro atomicamente. Tribunal pode ser nulo
antes da primeira confirmação; graus observados exigem tribunal confirmado.

Os testes usam um PostgreSQL temporário separado: veja [testes](src/test/README.md).
