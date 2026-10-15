ALTER TABLE consulta_grau RENAME TO tentativa_consulta_grau;

CREATE TABLE rodada (
    id uuid PRIMARY KEY,
    proprietario_id varchar(11) NOT NULL REFERENCES usuario (id_consultante),
    executor_id varchar(11) NOT NULL REFERENCES usuario (id_consultante),
    escopo varchar(10) NOT NULL CHECK (escopo IN ('LISTA', 'PROCESSO')),
    lista_id uuid,
    nome_lista text,
    janela_dias integer NOT NULL CHECK (janela_dias >= 0),
    data_referencia date NOT NULL,
    zona_referencia text NOT NULL CHECK (length(trim(zona_referencia)) > 0),
    estado varchar(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (estado IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA', 'INTERROMPIDA')),
    inicio timestamp with time zone,
    fim timestamp with time zone,
    cancelamento_solicitado boolean NOT NULL DEFAULT false,
    UNIQUE (id, proprietario_id),
    CHECK ((lista_id IS NULL) = (nome_lista IS NULL)),
    CHECK (escopo <> 'LISTA' OR lista_id IS NOT NULL),
    CHECK (fim IS NULL OR inicio IS NULL OR fim >= inicio),
    CHECK (
        (estado = 'PENDENTE' AND inicio IS NULL AND fim IS NULL)
        OR (estado = 'EM_ANDAMENTO' AND inicio IS NOT NULL AND fim IS NULL)
        OR (estado = 'CONCLUIDA' AND inicio IS NOT NULL AND fim IS NOT NULL)
        OR (estado IN ('CANCELADA', 'INTERROMPIDA') AND fim IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_rodada_executor_ativa ON rodada (executor_id)
    WHERE estado IN ('PENDENTE', 'EM_ANDAMENTO');

ALTER TABLE processo_monitorado ADD CONSTRAINT uq_processo_id_proprietario UNIQUE (id, proprietario_id);

CREATE TABLE item_rodada (
    id uuid PRIMARY KEY,
    rodada_id uuid NOT NULL,
    proprietario_id varchar(11) NOT NULL,
    processo_id uuid NOT NULL,
    numero_cnj varchar(20) NOT NULL CHECK (numero_cnj ~ '^[0-9]{20}$'),
    ordem integer NOT NULL CHECK (ordem >= 0),
    FOREIGN KEY (rodada_id, proprietario_id) REFERENCES rodada (id, proprietario_id),
    FOREIGN KEY (processo_id, proprietario_id) REFERENCES processo_monitorado (id, proprietario_id),
    UNIQUE (rodada_id, processo_id),
    UNIQUE (rodada_id, numero_cnj),
    UNIQUE (rodada_id, ordem),
    UNIQUE (id, processo_id)
);

CREATE TABLE consulta_grau (
    id uuid PRIMARY KEY,
    item_rodada_id uuid NOT NULL,
    processo_id uuid NOT NULL,
    tribunal varchar(4) NOT NULL CHECK (tribunal IN ('TRF1', 'TRF6')),
    grau varchar(20) NOT NULL CHECK (grau IN ('PRIMEIRO_GRAU', 'SEGUNDO_GRAU')),
    endpoint text NOT NULL CHECK (length(trim(endpoint)) > 0),
    estado varchar(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (estado IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA', 'INTERROMPIDA')),
    inicio timestamp with time zone,
    fim timestamp with time zone,
    FOREIGN KEY (item_rodada_id, processo_id) REFERENCES item_rodada (id, processo_id),
    UNIQUE (item_rodada_id, grau),
    UNIQUE (id, processo_id, tribunal, grau),
    CHECK (fim IS NULL OR inicio IS NULL OR fim >= inicio),
    CHECK (
        (estado = 'PENDENTE' AND inicio IS NULL AND fim IS NULL)
        OR (estado = 'EM_ANDAMENTO' AND inicio IS NOT NULL AND fim IS NULL)
        OR (estado = 'CONCLUIDA' AND inicio IS NOT NULL AND fim IS NOT NULL)
        OR (estado IN ('CANCELADA', 'INTERROMPIDA') AND fim IS NOT NULL)
    )
);

ALTER TABLE tentativa_consulta_grau
    ADD COLUMN consulta_planejada_id uuid,
    ADD COLUMN numero_tentativa integer,
    ADD COLUMN movimentacoes_exibidas integer,
    ADD CONSTRAINT ck_tentativa_planejada CHECK (
        (consulta_planejada_id IS NULL AND numero_tentativa IS NULL)
        OR (consulta_planejada_id IS NOT NULL AND numero_tentativa IS NOT NULL AND numero_tentativa > 0)
    ),
    ADD CONSTRAINT ck_tentativa_exibidas CHECK (
        movimentacoes_exibidas IS NULL
        OR (movimentacoes_exibidas >= 0 AND movimentacoes_exibidas <= novas_movimentacoes)
    ),
    ADD CONSTRAINT fk_tentativa_planejada FOREIGN KEY (consulta_planejada_id, processo_id, tribunal, grau)
        REFERENCES consulta_grau (id, processo_id, tribunal, grau),
    ADD CONSTRAINT uq_tentativa_numero UNIQUE (consulta_planejada_id, numero_tentativa),
    ADD CONSTRAINT uq_tentativa_consulta UNIQUE (id, consulta_planejada_id);

ALTER TABLE consulta_grau
    ADD COLUMN tentativa_atual_id uuid,
    ADD CONSTRAINT ck_consulta_resultado CHECK (
        (estado = 'CONCLUIDA') = (tentativa_atual_id IS NOT NULL)
    ),
    ADD CONSTRAINT fk_consulta_tentativa_atual FOREIGN KEY (tentativa_atual_id, id)
        REFERENCES tentativa_consulta_grau (id, consulta_planejada_id);

COMMENT ON COLUMN movimentacao_detectada.consulta_grau_id IS
    'UUID da tentativa que primeiro detectou o movimento, nao da consulta planejada';
COMMENT ON COLUMN tentativa_consulta_grau.consulta_planejada_id IS
    'NULL para registros V2 e chamadas avulsas do fluxo anterior; nunca inventar uma rodada';
