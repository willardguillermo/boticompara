package com.boticompara.usuario.security;

import com.boticompara.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarios;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarios) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            // H19: además de la firma, la cuenta debe seguir activa (si se eliminó, el token deja de valer)
            jwtService.validar(header.substring(7).trim())
                    .filter(u -> usuarios.existsByIdAndActivoTrue(u.id()))
                    .ifPresent(u -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.rol())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}