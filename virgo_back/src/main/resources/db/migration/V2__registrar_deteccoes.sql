-- Resultado de uma chamada a um grau. Rodadas e suas tentativas serao vinculadas
-- quando o fluxo de execucao for implementado; cada chamada usa um novo UUID.
CREATE TABLE consulta_grau (
    id uuid PRIMARY KEY,
    processo_id uuid NOT NULL REFERENCES processo_monitorado (id),
    tribunal varchar(4) NOT NULL CHECK (tribunal IN ('TRF1', 'TRF6')),
    grau varchar(20) NOT NULL CHECK (grau IN ('PRIMEIRO_GRAU', 'SEGUNDO_GRAU')),
    status varchar(20) NOT NULL CHECK (status IN (
        'ENCONTRADO', 'NAO_ENCONTRADO', 'PROCESSO_INVALIDO',
        'ACESSO_NEGADO', 'RESPOSTA_INVALIDA', 'INDISPONIVEL'
    )),
    erro text,
    registrada_em timestamp with time zone NOT NULL,
    novas_movimentacoes integer NOT NULL DEFAULT 0 CHECK (novas_movimentacoes >= 0),
    UNIQUE (id, processo_id, tribunal, grau, status),
    CHECK ((status = 'ENCONTRADO' AND erro IS NULL)
        OR (status <> 'ENCONTRADO' AND erro IS NOT NULL AND length(trim(erro)) > 0)),
    CHECK (status = 'ENCONTRADO' OR novas_movimentacoes = 0)
);

CREATE TABLE movimentacao_detectada (
    processo_id uuid NOT NULL,
    grau varchar(20) NOT NULL,
    id_movimentacao text NOT NULL CHECK (length(trim(id_movimentacao)) > 0),
    tribunal varchar(4) NOT NULL,
    data_hora timestamp without time zone NOT NULL,
    tipo varchar(10) NOT NULL CHECK (tipo IN ('NACIONAL', 'LOCAL')),
    codigo integer NOT NULL,
    descricao text NOT NULL,
    primeira_deteccao timestamp with time zone NOT NULL,
    consulta_grau_id uuid NOT NULL,
    -- A FK tambem garante que a origem corresponde a uma consulta bem-sucedida.
    status_consulta varchar(20) NOT NULL DEFAULT 'ENCONTRADO' CHECK (status_consulta = 'ENCONTRADO'),
    PRIMARY KEY (processo_id, grau, id_movimentacao),
    FOREIGN KEY (consulta_grau_id, processo_id, tribunal, grau, status_consulta)
        REFERENCES consulta_grau (id, processo_id, tribunal, grau, status)
);

CREATE INDEX idx_deteccao_consulta ON movimentacao_detectada (consulta_grau_id);
