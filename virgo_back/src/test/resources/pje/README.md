# Fixture do mapper PJe

[consulta-movimentos.xml](consulta-movimentos.xml) contém dados sintéticos. A estrutura usa como referência o
[WSDL público do TRF1](https://pje1g.trf1.jus.br/pje/intercomunicacao?wsdl) (2026-09-29)

A fixture testa o mapeamento do payload `consultarProcessoResposta`, extraído
do envelope SOAP. Não é um teste de validação de schema completa.

- Os namespaces, `dataHora` e os movimentos nacionais/locais exercitam a leitura
  dos campos esperados.
- IDs negativos e não numéricos verificam que os identificadores são preservados
  como texto. Complementos com caracteres escapados e CDATA exercitam a descrição.
- O movimento dentro de `documento` é uma distração intencional: não deve ser
  confundido com um movimento diretamente associado ao processo.
- `idDocumentoVinculado`, o complemento do movimento local e
  `movimentoLocalPai` verificam que textos alheios à descrição não são incorporados.

As regras de validação e os casos de campos ausentes ou inválidos ficam em
[PjeResponseMapperTest](../../java/br/gov/agu/virgo_back/pje/PjeResponseMapperTest.java).
Ao editar a fixture, preserve essas distinções para não enfraquecer os testes.
