package br.gov.agu.virgo_back.identidade.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record Usuario(IdConsultante id, @Nullable String nomeCompleto, @Nullable String email,
                      @Nullable String telefone, Perfil perfil, SituacaoUsuario situacao,
                      boolean cadastroCompleto, Instant criadoEm) {
    public Usuario {
        Objects.requireNonNull(id);
        Objects.requireNonNull(perfil);
        Objects.requireNonNull(situacao);
        Objects.requireNonNull(criadoEm);
        nomeCompleto = normalizar(nomeCompleto);
        email = normalizar(email);
        telefone = normalizar(telefone);
        if (cadastroCompleto && (nomeCompleto == null || email == null || telefone == null)) {
            throw new IllegalArgumentException("Cadastro completo exige nome, email e telefone");
        }
    }

    private static @Nullable String normalizar(@Nullable String valor) {
        if (valor == null) return null;
        if (valor.isBlank()) throw new IllegalArgumentException("Contato informado nao pode estar em branco");
        return valor.strip();
    }

    public Usuario completarCadastro(String nomeCompleto, String email, String telefone) {
        return new Usuario(id, nomeCompleto, email, telefone, perfil, situacao, true, criadoEm);
    }
}
