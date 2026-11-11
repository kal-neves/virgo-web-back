package br.gov.agu.virgo_back.identidade.persistence;

import br.gov.agu.virgo_back.identidade.domain.*;

import java.time.Instant;
import java.util.Optional;

public interface UsuarioRepository {
    Optional<Usuario> buscarPorId(IdConsultante id);

    Usuario criarSeAusente(IdConsultante id, Instant criadoEm);

    boolean completarCadastro(IdConsultante id, String nomeCompleto, String email, String telefone);

    boolean alterarPerfil(IdConsultante id, Perfil perfil);

    boolean alterarSituacao(IdConsultante id, SituacaoUsuario situacao);
}
