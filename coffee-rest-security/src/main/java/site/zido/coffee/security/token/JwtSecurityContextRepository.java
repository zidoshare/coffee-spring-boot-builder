package site.zido.coffee.security.token;

import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;

/**
 * @author zido
 */
public class JwtSecurityContextRepository implements SecurityContextRepository {
    public static final String DEFAULT_AUTH_HEADER_NAME = "Authorization";
    private static Logger LOGGER = LoggerFactory.getLogger(JwtSecurityContextRepository.class);
    private String authHeaderName = DEFAULT_AUTH_HEADER_NAME;
    private AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    private String jwtSecret;

    private long jwtExpirationInMs;

    private String issue = "coffee-security";

    private UserDetailsService userService;

    private GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();

    public JwtSecurityContextRepository() {
    }

    public JwtSecurityContextRepository(String jwtSecret, long jwtExpirationInMs) {
        this.jwtSecret = Base64.getEncoder().encodeToString(jwtSecret.getBytes());
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        HttpServletRequest request = requestResponseHolder.getRequest();
        HttpServletResponse response = requestResponseHolder.getResponse();
        String token = request.getHeader(authHeaderName);
        SecurityContext context;
        if (token == null) {
            LOGGER.debug("No token currently exists");
            context = generateNewContext();
            requestResponseHolder.setResponse(new JwtWriterResponse(response, null));
        } else {
            try {
                if (StringUtils.isEmpty(token)) {
                    return null;
                }
                Claims claims;
                try {
                    claims = Jwts.parser()
                            .setSigningKey(jwtSecret)
                            .parseClaimsJws(token)
                            .getBody();
                } catch (ExpiredJwtException | UnsupportedJwtException e) {
                    throw new TokenInvalidException("token失效", e);
                } catch (MalformedJwtException e) {
                    LOGGER.warn("jwt token被修改过:{}", token);
                    throw new TokenInvalidException("token失效", e);
                } catch (SignatureException e) {
                    LOGGER.warn("签名异常:{}", token);
                    throw new TokenInvalidException("token失效", e);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("token串非法:{}", token);
                    throw new TokenInvalidException("token失效", e);
                }
                String username = claims.getSubject();
                if (username == null) {
                    return null;
                }

                UserDetails user = userService.loadUserByUsername(username);
                context = new SecurityContextImpl();
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, null,
                        authoritiesMapper.mapAuthorities(user.getAuthorities()));
                context.setAuthentication(authenticationToken);

                requestResponseHolder.setResponse(new JwtWriterResponse(response, claims));
            } catch (TokenInvalidException e) {
                context = generateNewContext();
            }
        }
        LOGGER.debug("Obtained a valid SecurityContext from " + authHeaderName
                + " in request header"
                + ": '" + context + "'");
        return context;
    }

    protected SecurityContext generateNewContext() {
        return SecurityContextHolder.createEmptyContext();
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        if (response instanceof JwtWriterResponse) {
            ((JwtWriterResponse) response).writeHeaders(context);
        }
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return StringUtils.hasLength(request.getHeader(authHeaderName));
    }

    private void addTokenToResponse(HttpServletResponse response, String token) {
        response.setHeader("Authorization", token);
    }

    private String generateNewToken(SecurityContext subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) (jwtExpirationInMs * 1.5));

        return Jwts.builder()
                .setSubject(subject.getAuthentication().getName())
                .setIssuedAt(now)
                .setIssuer(issue)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

    class JwtWriterResponse extends OnCommittedResponseWrapper {
        private final Claims claims;

        JwtWriterResponse(HttpServletResponse response,
                          Claims claims) {
            super(response);
            this.claims = claims;
        }

        @Override
        protected void onResponseCommitted() {
            writeHeaders(SecurityContextHolder.getContext());
            this.disableOnResponseCommitted();
        }

        protected void writeHeaders(SecurityContext context) {
            if (isDisableOnResponseCommitted()) {
                return;
            }
            if (claims == null) {
                String newToken = generateNewToken(context);
                addTokenToResponse(getHttpResponse(), newToken);
            } else {
                Date issued = claims.getIssuedAt();
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(issued);
                calendar.add(Calendar.MILLISECOND, (int) (jwtExpirationInMs * 0.5));
                if (calendar.getTime().before(new Date())) {
                    String newToken = generateNewToken(context);
                    addTokenToResponse(getHttpResponse(), newToken);
                }
            }
        }

        private HttpServletResponse getHttpResponse() {
            return (HttpServletResponse) getResponse();
        }
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public void setTrustResolver(AuthenticationTrustResolver trustResolver) {
        this.trustResolver = trustResolver;
    }

    public void setUserService(UserDetailsService userService) {
        this.userService = userService;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = Base64.getEncoder().encodeToString(jwtSecret.getBytes());
    }

    public void setJwtExpirationInMs(long jwtExpirationInMs) {
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    public void setAuthoritiesMapper(GrantedAuthoritiesMapper authoritiesMapper) {
        this.authoritiesMapper = authoritiesMapper;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }
}
