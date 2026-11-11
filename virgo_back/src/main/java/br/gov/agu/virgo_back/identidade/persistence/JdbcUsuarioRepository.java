package br.gov.agu.virgo_back.identidade.persistence;

import br.gov.agu.virgo_back.identidade.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcUsuarioRepository implements UsuarioRepository {
    private final JdbcClient jdbc;

    public JdbcUsuarioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscarPorId(IdConsultante id) {
        return jdbc.sql("SELECT * FROM usuario WHERE id_consultante = ?").param(id.valor())
                .query((rs, row) -> new Usuario(new IdConsultante(rs.getString("id_consultante")),
                        rs.getString("nome_completo"), rs.getString("email"), rs.getString("telefone"),
                        Perfil.valueOf(rs.getString("perfil")), SituacaoUsuario.valueOf(rs.getString("situacao")),
                        rs.getBoolean("cadastro_completo"), rs.getObject("criado_em", OffsetDateTime.class).toInstant()))
                .optional();
    }

    /**
     * cria um usuário comum ativo com cadastro incompleto quando o CPF ainda não existe
     * aguarda uma criação concorrente e lê o registro confirmado sem sobrescrever seus dados
     * o chamador deve validar as credenciais antes de usar esta operação
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Usuario criarSeAusente(IdConsultante id, Instant criadoEm) {
        jdbc.sql("""
                INSERT INTO usuario (id_consultante, criado_em) VALUES (?, ?)
                ON CONFLICT (id_consultante) DO NOTHING
                """).params(id.valor(), Objects.requireNonNull(criadoEm).atOffset(ZoneOffset.UTC)).update();
        return buscarPorId(id).orElseThrow();
    }

    @Override
    public boolean completarCadastro(IdConsultante id, String nomeCompleto, String email, String telefone) {
        var existente = buscarPorId(id);
        if (existente.isEmpty()) return false;
        var dados = existente.get().completarCadastro(nomeCompleto, email, telefone);
        return jdbc.sql("""
                UPDATE usuario SET nome_completo = ?, email = ?, telefone = ?, cadastro_completo = true
                WHERE id_consultante = ?
                """).params(dados.nomeCompleto(), dados.email(), dados.telefone(), id.valor()).update() == 1;
    }

    @Override
    public boolean alterarPerfil(IdConsultante id, Perfil perfil) {
        return jdbc.sql("UPDATE usuario SET perfil = ? WHERE id_consultante = ?")
                .params(Objects.requireNonNull(perfil).name(), id.valor()).update() == 1;
    }

    @Override
    public boolean alterarSituacao(IdConsultante id, SituacaoUsuario situacao) {
        return jdbc.sql("UPDATE usuario SET situacao = ? WHERE id_consultante = ?")
                .params(Objects.requireNonNull(situacao).name(), id.valor()).update() == 1;
    }
}
