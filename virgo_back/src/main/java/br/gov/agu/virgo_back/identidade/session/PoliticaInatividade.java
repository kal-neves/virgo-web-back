package br.gov.agu.virgo_back.identidade.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class PoliticaInatividade {
    public boolean expirou(SessaoVirgo sessao, Instant agora) {
        Objects.requireNonNull(sessao);
        Objects.requireNonNull(agora);
        return sessao.consultasAtivas() == 0
                && Duration.between(sessao.ultimaAtividade(), agora).compareTo(Duration.ofMinutes(60)) >= 0;
    }
}
