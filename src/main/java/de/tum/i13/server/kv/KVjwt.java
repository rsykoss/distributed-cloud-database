package de.tum.i13.server.kv;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;

public class KVjwt {
    // private Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    String password = "0E7F1CD575C5CBB1C99F65AB6AAC88499E08B946388588128AE5614E39113B99";
    private Key key = Keys.hmacShaKeyFor(password.getBytes(StandardCharsets.ISO_8859_1));

    public String createJWT(String aud, String subject) {
        String jws = Jwts.builder().setAudience(aud).setSubject(subject).signWith(this.key).compact();
        return jws;
    }

    public String decodeJWT(String jws) {
        try {
            Jwts.parser().setSigningKey(key).parseClaimsJws(jws);
            String msg = Jwts.parser().setSigningKey(this.key).parseClaimsJws(jws).getBody().getSubject();
            return "validToken " + msg;
        } catch (JwtException e) {
            return "invalidToken";
        }
    }
}