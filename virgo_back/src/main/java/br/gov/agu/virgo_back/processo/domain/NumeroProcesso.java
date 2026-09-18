package br.gov.agu.virgo_back.processo.domain;

/**
 * Número CNJ com 20 dígitos, preservando zeros na esquerda.
 * Dígitos puros ou NNNNNNN-DD.AAAA.J.TR.OOOO e espaços externos.
 * Valida formato, sem verificar existência do processo
 */

public record NumeroProcesso(String valor) {

    public NumeroProcesso {
        if (valor == null) {
            throw new IllegalArgumentException("O número do processo é obrigatório");
        }

        valor = valor.strip();

        if (valor.matches("[0-9]{7}-[0-9]{2}\\.[0-9]{4}\\.[0-9]\\.[0-9]{2}\\.[0-9]{4}")) {
            valor = valor.replace("-", "").replace(".", "");
        } else if (!valor.matches("[0-9]{20}")) {
            throw new IllegalArgumentException(
                    "O número do processo deve conter 20 dígitos ou usar o formato NNNNNNN-DD.AAAA.J.TR.OOOO"
            );
        }
    }
}
