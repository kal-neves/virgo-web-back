ALTER TABLE usuario
    ADD COLUMN nome_completo text,
    ADD COLUMN email text,
    ADD COLUMN telefone text,
    ADD COLUMN perfil varchar(10) NOT NULL DEFAULT 'COMUM',
    ADD COLUMN situacao varchar(10) NOT NULL DEFAULT 'ATIVO',
    ADD COLUMN cadastro_completo boolean NOT NULL DEFAULT false,
    ADD COLUMN criado_em timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('COMUM', 'ADMIN')),
    ADD CONSTRAINT ck_usuario_situacao CHECK (situacao IN ('ATIVO', 'INATIVO')),
    ADD CONSTRAINT ck_usuario_nome CHECK (nome_completo IS NULL OR nome_completo ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_usuario_email CHECK (email IS NULL OR email ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_usuario_telefone CHECK (telefone IS NULL OR telefone ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_usuario_cadastro CHECK (
        NOT cadastro_completo OR (nome_completo IS NOT NULL AND email IS NOT NULL AND telefone IS NOT NULL)
    );
