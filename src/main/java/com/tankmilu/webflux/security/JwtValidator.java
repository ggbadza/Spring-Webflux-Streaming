package com.tankmilu.webflux.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidator {

    private final JwtProvider jwtProvider;

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(jwtProvider.getSecretKeyHmac())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException e) {
            // 서명 오류
            System.out.println("Invalid JWT signature: " + e.getMessage());
        } catch (Exception e) {
            // 기타 오류
            System.out.println("Invalid JWT token: " + e.getMessage());
        }
        return false;
    }

    // 토큰에서 사용자 ID 추출
    public String extractUserId(String token) {
        return Jwts.parser()
                .verifyWith(jwtProvider.getSecretKeyHmac())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // 토큰에서 세션 코드 추출
    public String extractSessionCode(String refreshToken) {
        return Jwts.parser()
                .verifyWith(jwtProvider.getSecretKeyHmac())
                .build()
                .parseSignedClaims(refreshToken)
                .getPayload()
                .get("sessionCode", String.class);
    }

    // 토큰에서 클레임 추출
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtProvider.getSecretKeyHmac())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
