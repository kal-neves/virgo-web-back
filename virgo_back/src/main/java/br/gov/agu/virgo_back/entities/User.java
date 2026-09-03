package br.gov.agu.virgo_back.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @NotBlank
    private String senha;

    @NotBlank
    private String login;

}
