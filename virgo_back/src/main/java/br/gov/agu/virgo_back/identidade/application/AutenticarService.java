package br.gov.agu.virgo_back.identidade.application;

import br.gov.agu.virgo_back.consulta.application.ValidarCredenciaisGateway;
import br.gov.agu.virgo_back.identidade.domain.SituacaoUsuario;
import br.gov.agu.virgo_back.identidade.domain.Usuario;
import br.gov.agu.virgo_back.identidade.persistence.UsuarioRepository;
import br.gov.agu.virgo_back.identidade.session.CredenciaisSessaoStore;
import br.gov.agu.virgo_back.identidade.session.SessaoVirgo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
public class AutenticarService {
    private final ValidarCredenciaisGateway gateway;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final CredenciaisSessaoStore sessoes;

    public AutenticarService(ValidarCredenciaisGateway gateway, UsuarioRepository usuarios, Clock clock,
                            CredenciaisSessaoStore sessoes) {
        this.gateway = gateway;
        this.usuarios = usuarios;
        this.clock = clock;
        this.sessoes = sessoes;
    }

    /**
     * verifica o PJe sem manter a transação do chamador aberta
     * cria a sessão em memória somente depois de confirmar a identidade ativa
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SessaoVirgo iniciarSessao(CredenciaisPje credenciais) {
        return sessoes.criar(autenticar(credenciais), credenciais);
    }

    /**
     * suspende a transação do chamador enquanto o PJe verifica as credenciais
     * cria ou carrega a identidade local somente depois da confirmação do PJe
     * o repositório confirma a criação sem alterar os dados de um usuário existente
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Usuario autenticar(CredenciaisPje credenciais) {
        Objects.requireNonNull(credenciais);
        var usuario = switch (gateway.validar(credenciais)) {
            case INVALIDAS -> throw new SecurityException("Credenciais PJe invalidas");
            case INDISPONIVEL -> throw new IllegalStateException("PJe indisponivel para autenticacao");
            case RESPOSTA_INVALIDA -> throw new IllegalStateException("PJe nao confirmou as credenciais");
            case VALIDAS -> usuarios.criarSeAusente(credenciais.login(), clock.instant());
        };
        if (usuario.situacao() == SituacaoUsuario.INATIVO) {
            throw new SecurityException("Usuario inativo");
        }
        return usuario;
    }
}
