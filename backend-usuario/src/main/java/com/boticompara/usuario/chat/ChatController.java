package com.boticompara.usuario.chat;

import com.boticompara.usuario.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chat;

    /**
     * H68: requiere sesión (cae en anyRequest().authenticated() de SecurityConfig). El id y el rol salen del
     * token: el cuerpo no puede decir quién pregunta.
     */
    @PostMapping
    public ChatResponse preguntar(@RequestBody ChatRequest pedido, @AuthenticationPrincipal UsuarioAutenticado yo) {
        return chat.responder(yo, pedido);
    }
}
