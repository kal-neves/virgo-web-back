package br.gov.agu.virgo_back.processo.domain;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProcessoMonitorado(
        UUID id,
        IdConsultante proprietario,
        NumeroProcesso numeroProcesso,
        @Nullable Tribunal tribunalConfirmado,
        Set<GrauJurisdicao> grausObservados,
        String anotacao,
        boolean atencao
) {
    public ProcessoMonitorado {
        Objects.requireNonNull(id, "O ID do processo monitorado é obrigatório");
        Objects.requireNonNull(proprietario, "O proprietário é obrigatório");
        Objects.requireNonNull(numeroProcesso, "O número do processo é obrigatório");
        Objects.requireNonNull(grausObservados, "Os graus observados são obrigatórios");
        Objects.requireNonNull(anotacao, "A anotação é obrigatória, podendo ser vazia");
        grausObservados = Set.copyOf(grausObservados);
        if (tribunalConfirmado == null && !grausObservados.isEmpty()) {
            throw new IllegalArgumentException("Graus observados exigem um tribunal confirmado");
        }
    }
}
