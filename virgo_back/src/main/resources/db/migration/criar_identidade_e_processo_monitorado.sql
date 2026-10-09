-- perfil e autenticacao ainda wip
-- nenhuma credencial
CREATE TABLE usuario (
    id_consultante varchar(11) PRIMARY KEY,
    CONSTRAINT ck_usuario_cpf CHECK (id_consultante ~ '^[0-9]{11}$')
);

CREATE TABLE processo_monitorado (
    id uuid PRIMARY KEY,
    proprietario_id varchar(11) NOT NULL REFERENCES usuario (id_consultante),
    numero_cnj varchar(20) NOT NULL,
    tribunal_confirmado varchar(4),
    primeiro_grau_observado boolean NOT NULL DEFAULT false,
    segundo_grau_observado boolean NOT NULL DEFAULT false,
    anotacao text NOT NULL DEFAULT '',
    atencao boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_processo_proprietario_cnj UNIQUE (proprietario_id, numero_cnj),
    CONSTRAINT ck_processo_cnj CHECK (numero_cnj ~ '^[0-9]{20}$'),
    CONSTRAINT ck_processo_tribunal CHECK (tribunal_confirmado IN ('TRF1', 'TRF6')),
    CONSTRAINT ck_processo_graus_tribunal CHECK (
        tribunal_confirmado IS NOT NULL
        OR (NOT primeiro_grau_observado AND NOT segundo_grau_observado)
    )
);
