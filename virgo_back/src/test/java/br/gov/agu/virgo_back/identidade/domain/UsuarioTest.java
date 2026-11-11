package br.gov.agu.virgo_back.identidade.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioTest {
    private final Usuario usuario = new Usuario(new IdConsultante("01234567890"), null, null, null,
            Perfil.ADMIN, SituacaoUsuario.INATIVO, false, Instant.parse("2026-11-11T12:00:00Z"));

    @Test
    void completaContatosSemAlterarIdentidadePermissoesOuCriacao() {
        var completo = usuario.completarCadastro(" Maria da Silva ", " maria@example.test ", " +55 31 99999-0000 ");
        assertTrue(completo.cadastroCompleto());
        assertEquals("Maria da Silva", completo.nomeCompleto());
        assertEquals("maria@example.test", completo.email());
        assertEquals("+55 31 99999-0000", completo.telefone());
        assertEquals(usuario.id(), completo.id());
        assertEquals(usuario.perfil(), completo.perfil());
        assertEquals(usuario.situacao(), completo.situacao());
        assertEquals(usuario.criadoEm(), completo.criadoEm());
        assertFalse(usuario.cadastroCompleto());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void cadastroCompletoExigeTodosOsContatos(String ausente) {
        assertThrows(IllegalArgumentException.class, () -> usuario.completarCadastro(ausente, "a@b.test", "123"));
        assertThrows(IllegalArgumentException.class, () -> usuario.completarCadastro("Maria Silva", ausente, "123"));
        assertThrows(IllegalArgumentException.class, () -> usuario.completarCadastro("Maria Silva", "a@b.test", ausente));
    }

    @Test
    void cadastroParcialNaoSeTornaCompletoAutomaticamente() {
        var parcial = new Usuario(usuario.id(), "Maria Silva", null, null, Perfil.COMUM,
                SituacaoUsuario.ATIVO, false, usuario.criadoEm());
        assertFalse(parcial.cadastroCompleto());
        assertNull(parcial.email());
        assertThrows(IllegalArgumentException.class, () -> new Usuario(usuario.id(), " ", null, null,
                Perfil.COMUM, SituacaoUsuario.ATIVO, false, usuario.criadoEm()));
    }

    @Test
    void identidadeEstadoECriacaoSaoObrigatorios() {
        assertThrows(NullPointerException.class, () -> new Usuario(null, null, null, null, Perfil.COMUM, SituacaoUsuario.ATIVO, false, usuario.criadoEm()));
        assertThrows(NullPointerException.class, () -> new Usuario(usuario.id(), null, null, null, null, SituacaoUsuario.ATIVO, false, usuario.criadoEm()));
        assertThrows(NullPointerException.class, () -> new Usuario(usuario.id(), null, null, null, Perfil.COMUM, null, false, usuario.criadoEm()));
        assertThrows(NullPointerException.class, () -> new Usuario(usuario.id(), null, null, null, Perfil.COMUM, SituacaoUsuario.ATIVO, false, null));
    }
}
