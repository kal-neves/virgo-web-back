package br.gov.agu.virgo_back.shared.security;

import br.gov.agu.virgo_back.identidade.domain.SituacaoUsuario;
import br.gov.agu.virgo_back.identidade.persistence.UsuarioRepository;
import br.gov.agu.virgo_back.identidade.session.CredenciaisSessaoStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    public static final String COOKIE_SESSAO = "__Host-VIRGO_SESSION";

    @Bean
    AuthenticationManager autenticacaoSessao(CredenciaisSessaoStore sessoes, UsuarioRepository usuarios) {
        return solicitacao -> {
            if (!(solicitacao instanceof PreAuthenticatedAuthenticationToken)
                    || !(solicitacao.getPrincipal() instanceof UUID id)) {
                throw new BadCredentialsException("Sessao invalida");
            }
            var sessao = sessoes.buscar(id)
                    .orElseThrow(() -> new BadCredentialsException("Sessao ausente ou expirada"));
            var usuario = usuarios.buscarPorId(sessao.usuario()).orElse(null);
            if (usuario == null || usuario.situacao() != SituacaoUsuario.ATIVO) {
                sessoes.encerrar(id);
                throw new BadCredentialsException("Sessao indisponivel");
            }
            var permissoes = new ArrayList<SimpleGrantedAuthority>();
            permissoes.add(new SimpleGrantedAuthority("ROLE_" + usuario.perfil().name()));
            if (usuario.cadastroCompleto()) permissoes.add(new SimpleGrantedAuthority("CADASTRO_COMPLETO"));
            return new PreAuthenticatedAuthenticationToken(sessao, null, permissoes);
        };
    }

    @Bean
    CookieCsrfTokenRepository csrfTokens() {
        var tokens = new CookieCsrfTokenRepository();
        tokens.setCookieName("__Host-VIRGO_CSRF");
        tokens.setCookieCustomizer(cookie -> cookie.path("/").secure(true).httpOnly(true).sameSite("Lax"));
        return tokens;
    }

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, AuthenticationManager autenticacaoSessao,
                                  CookieCsrfTokenRepository csrfTokens) throws Exception {
        return http
                .authenticationManager(autenticacaoSessao)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint((_, resposta, _) -> resposta.setStatus(401))
                        .accessDeniedHandler((_, resposta, _) -> resposta.setStatus(403)))
                .authorizeHttpRequests(acesso -> acesso
                        .requestMatchers(HttpMethod.GET, "/api/sessao/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sessao").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sessao", "/api/perfil").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/sessao").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/sessao/atividade").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/perfil").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new FiltroSessao(autenticacaoSessao), CsrfFilter.class)
                .build();
    }

    public static ResponseCookie cookieSessao(UUID id) {
        return ResponseCookie.from(COOKIE_SESSAO, id.toString())
                .path("/").secure(true).httpOnly(true).sameSite("Lax").build();
    }

    public static ResponseCookie removerCookieSessao() {
        return ResponseCookie.from(COOKIE_SESSAO, "")
                .path("/").secure(true).httpOnly(true).sameSite("Lax").maxAge(0).build();
    }

    @NullMarked
    private static final class FiltroSessao extends OncePerRequestFilter {
        private final AuthenticationManager autenticacao;

        private FiltroSessao(AuthenticationManager autenticacao) {
            this.autenticacao = autenticacao;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String valor = null;
            boolean repetido = false;
            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    if (COOKIE_SESSAO.equals(cookie.getName())) {
                        if (valor != null) repetido = true;
                        valor = cookie.getValue();
                    }
                }
            }
            if (valor != null) {
                try {
                    var id = UUID.fromString(valor);
                    if (repetido || !id.toString().equals(valor)) throw new IllegalArgumentException();
                    var autenticado = autenticacao.authenticate(new PreAuthenticatedAuthenticationToken(id, null));
                    var contexto = SecurityContextHolder.createEmptyContext();
                    contexto.setAuthentication(autenticado);
                    SecurityContextHolder.setContext(contexto);
                } catch (IllegalArgumentException | AuthenticationException _) {
                    SecurityContextHolder.clearContext();
                    response.addHeader(HttpHeaders.SET_COOKIE, removerCookieSessao().toString());
                }
            }
            chain.doFilter(request, response);
        }
    }
}
