package br.gov.agu.virgo_back.identidade.application;

import br.gov.agu.virgo_back.consulta.application.ValidarCredenciaisGateway;
import br.gov.agu.virgo_back.identidade.domain.*;
import br.gov.agu.virgo_back.identidade.persistence.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticarServiceTest {
    private final ValidarCredenciaisGateway gateway = mock(ValidarCredenciaisGateway.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final Instant agora = Instant.parse("2026-11-11T15:00:00Z");
    private final AutenticarService service = new AutenticarService(gateway, usuarios, Clock.fixed(agora, ZoneOffset.UTC));
    private final CredenciaisPje credenciais = new CredenciaisPje("senha-ficticia", new IdConsultante("01234567890"));

    @Test
    void validaAntesDeProvisionarEPreservaOnboardingPendente() {
        var usuario = new Usuario(credenciais.login(), null, null, null, Perfil.COMUM, SituacaoUsuario.ATIVO, false, agora);
        when(gateway.validar(credenciais)).thenReturn(ValidarCredenciaisGateway.Resultado.VALIDAS);
        when(usuarios.criarSeAusente(credenciais.login(), agora)).thenReturn(usuario);
        assertEquals(usuario, service.autenticar(credenciais));
        var ordem = inOrder(gateway, usuarios);
        ordem.verify(gateway).validar(credenciais);
        ordem.verify(usuarios).criarSeAusente(credenciais.login(), agora);
        ordem.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = ValidarCredenciaisGateway.Resultado.class, names = {"INVALIDAS", "INDISPONIVEL", "RESPOSTA_INVALIDA"})
    void falhaDeValidacaoNaoAcessaPersistencia(ValidarCredenciaisGateway.Resultado resultado) {
        when(gateway.validar(credenciais)).thenReturn(resultado);
        var falha = assertThrows(RuntimeException.class, () -> service.autenticar(credenciais));
        if (resultado == ValidarCredenciaisGateway.Resultado.INVALIDAS) assertInstanceOf(SecurityException.class, falha);
        else assertInstanceOf(IllegalStateException.class, falha);
        assertFalse(falha.getMessage().contains(credenciais.senha()));
        verify(gateway).validar(credenciais);
        verifyNoMoreInteractions(gateway);
        verifyNoInteractions(usuarios);
    }

    @Test
    void credenciaisValidasNaoReativamUsuarioInativo() {
        when(gateway.validar(credenciais)).thenReturn(ValidarCredenciaisGateway.Resultado.VALIDAS);
        when(usuarios.criarSeAusente(credenciais.login(), agora)).thenReturn(new Usuario(credenciais.login(),
                "Maria Silva", "maria@example.test", "123", Perfil.ADMIN, SituacaoUsuario.INATIVO, true, agora));
        assertThrows(SecurityException.class, () -> service.autenticar(credenciais));
        verify(usuarios).criarSeAusente(credenciais.login(), agora);
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void preservaAdministradorEContatosExistentes() {
        var usuario = new Usuario(credenciais.login(), "Maria Silva", "maria@example.test", "123",
                Perfil.ADMIN, SituacaoUsuario.ATIVO, true, agora.minusSeconds(100));
        when(gateway.validar(credenciais)).thenReturn(ValidarCredenciaisGateway.Resultado.VALIDAS);
        when(usuarios.criarSeAusente(credenciais.login(), agora)).thenReturn(usuario);
        assertEquals(usuario, service.autenticar(credenciais));
    }

    @Test
    void credenciaisAusentesOuResultadoAusenteNaoProvisionam() {
        assertThrows(NullPointerException.class, () -> service.autenticar(null));
        verifyNoInteractions(gateway);
        assertThrows(NullPointerException.class, () -> service.autenticar(credenciais));
        verifyNoInteractions(usuarios);
    }
}
