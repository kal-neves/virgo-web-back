# Fixtures do mapper

`consulta-movimentos.xml` contém dados sintéticos, sem credenciais ou dados judiciais reais.
Estrutura consultada em 2026-09-29:
[WSDL público TRF1](https://pje1g.trf1.jus.br/pje/intercomunicacao?wsdl).

O payload é `consultarProcessoResposta` no namespace de serviço; sucesso, mensagem e
processo usam tipos-servico; movimentos e seus filhos usam intercomunicacao.
O atributo temporal é `dataHora`. IDs são strings opacas, inclusive negativos;
os códigos são `xs:int`. Complementos são opcionais e podem ocorrer diretamente
no movimento e dentro de movimentoNacional. A descrição nacional reúne esses
campos em ordem, separados por quebra de linha; a local usa apenas `descricao`.

O documento aninhado e o pai do movimento local são distrações intencionais para
verificar que o mapper não percorre descendentes arbitrários nem mistura textos.
Este é um teste do mapeamento, não uma validação XSD completa. O mapper recebe
o payload já extraído pelo WebServiceTemplate, não o envelope SOAP.

A fixture não registra XSDs no IntelliJ. O comentário `suppress XmlHighlighting`
limita a supressão de avisos de validação XML ao elemento raiz desta fixture,
incluindo seus descendentes. Os namespaces permanecem intactos e continuam sendo
verificados pelos testes; não é necessário baixar recursos dos URIs de namespace.

Política do VIRGO: exigir ID não vazio e exatamente um tipo nacional/local, mesmo
que o WSDL permita esses campos ausentes. Um movimento inválido rejeita o resultado
inteiro daquela origem, sem movimentos parciais. Complemento ausente é permitido
e resulta em descrição vazia; nenhum texto substituto é inventado.

O teste de integração com PJe permanece opt-in e separado destes testes locais.
