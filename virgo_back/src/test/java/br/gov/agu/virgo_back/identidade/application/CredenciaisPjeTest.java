package br.gov.agu.virgo_back.identidade.application;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CredenciaisPjeTest {
    private final IdConsultante login = new IdConsultante("01234567890");

    @Test
    void preservaSenhaExataSemIncluiLaNoToString() {
        String senha = " senha<&>\"'á ";
        var credenciais = new CredenciaisPje(senha, login);
        assertEquals(senha, credenciais.senha());
        assertEquals(login, credenciais.login());
        assertFalse(credenciais.toString().contains(senha));
        assertFalse(credenciais.toString().contains(senha.strip()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejeitaSenhaEmBranco(String senha) {
        assertThrows(IllegalArgumentException.class, () -> new CredenciaisPje(senha, login));
    }

    @Test
    void exigeLoginESenha() {
        assertThrows(NullPointerException.class, () -> new CredenciaisPje(null, login));
        assertThrows(NullPointerException.class, () -> new CredenciaisPje("senha-ficticia", null));
    }
}
