package site.zido.coffee.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.util.UrlPathHelper;
import site.zido.coffee.auth.authentication.ProviderManager;
import site.zido.coffee.auth.authentication.UsernamePasswordAuthenticationProvider;
import site.zido.coffee.auth.authentication.UsernamePasswordClassProps;
import site.zido.coffee.auth.security.PasswordEncoder;
import site.zido.coffee.auth.user.annotations.AuthColumnPassword;
import site.zido.coffee.auth.user.annotations.AuthColumnUsername;
import site.zido.coffee.auth.user.annotations.AuthEntity;
import site.zido.coffee.auth.web.authentication.UsernamePasswordAuthenticationFilter;
import site.zido.coffee.auth.web.utils.matcher.AntPathRequestMatcher;
import site.zido.coffee.auth.web.utils.matcher.OrRequestMatcher;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zido
 */
public class AnnotationWebAuthConfigurerAdapter<T> extends WebAuthConfigurerAdapter {
    private final static Logger LOGGER = LoggerFactory.getLogger(AnnotationWebAuthConfigurerAdapter.class);
    private static final String DEFAULT_USERNAME = "username";
    private static final String DEFAULT_PASSWORD = "password";
    private String usernamePropsName = DEFAULT_USERNAME;
    private String passwordPropsName = DEFAULT_PASSWORD;
    private PasswordEncoder passwordEncoder;
    private UrlPathHelper urlPathHelper;
    private Class<T> userClass;

    public AnnotationWebAuthConfigurerAdapter(Class<T> userClass) {
        this.userClass = userClass;
    }

    @Override
    protected void configure(WebAuthUnit unit) {
        String simpleClassName = userClass.getSimpleName();
        simpleClassName = simpleClassName.substring(0, 1).toLowerCase() + simpleClassName.substring(1);
        AuthEntity authEntity = AnnotatedElementUtils
                .findMergedAnnotation(userClass, AuthEntity.class);
        if (authEntity == null) {
            return;
        }
        UsernamePasswordClassProps props = readProps(userClass, objectPostProcessor);
        if (props.getUsernameField() == null || props.getPasswordField() == null) {
            //不注册
            return;
        }
        boolean caseSensitive = authEntity.caseSensitive();
        String[] methods = authEntity.method();
        String url = authEntity.url();
        if (url.length() == 0) {
            url = "/" + simpleClassName + "/login";
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        String finalUrl = url;
        OrRequestMatcher requestMatcher = new OrRequestMatcher(
                Stream.of(methods).map(method ->
                        new AntPathRequestMatcher(finalUrl, method, caseSensitive, urlPathHelper))
                        .collect(Collectors.toList()));
        UsernamePasswordAuthenticationProvider provider = objectPostProcessor
                .postProcess(new UsernamePasswordAuthenticationProvider(null));
        provider.setPasswordEncoder(props.getPasswordEncoder());
        ProviderManager providerManager = objectPostProcessor
                .postProcess(new ProviderManager(Collections.singletonList(provider)));
        UsernamePasswordAuthenticationFilter authenticationFilter = objectPostProcessor
                .postProcess(new UsernamePasswordAuthenticationFilter(requestMatcher));
        authenticationFilter.setAuthenticationManager(providerManager);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("register usernamePasswordFilter for user entity:{}," +
                            "{username field name:{}," +
                            "password field name:{}," +
                            "password encoder:{}," +
                            "login url:{}}",
                    simpleClassName,
                    props.getUsernameField().getName(),
                    props.getPasswordField().getName(),
                    props.getPasswordEncoder(),
                    requestMatcher);
        }
    }

    private UsernamePasswordClassProps readProps(Class<?> userClass,
                                                 ObjectPostProcessor<Object> objectPostProcessor) {
        UsernamePasswordClassProps props = new UsernamePasswordClassProps();
        ReflectionUtils.doWithFields(userClass, field -> {
            if (AnnotatedElementUtils.findMergedAnnotation(field, AuthColumnUsername.class) != null) {
                props.setUsernameField(field);
            } else if (props.getUsernameField() == null && field.getName().equals(usernamePropsName)) {
                props.setUsernameField(field);
            }
            AuthColumnPassword passwordAnnotation;
            if ((passwordAnnotation = AnnotatedElementUtils.findMergedAnnotation(field, AuthColumnPassword.class)) != null) {
                Class<? extends PasswordEncoder> passwordEncoderClass = passwordAnnotation.encodeClass();
                try {
                    PasswordEncoder passwordEncoder =
                            objectPostProcessor.postProcess(passwordEncoderClass.newInstance());
                    props.setPasswordEncoder(passwordEncoder);
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                props.setPasswordField(field);
            } else if (props.getPasswordField() == null && field.getName().equals(passwordPropsName)) {
                props.setPasswordField(field);
                props.setPasswordEncoder(passwordEncoder);
            }
        });
        return props;
    }

    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void setUsernamePropsName(String usernamePropsName) {
        this.usernamePropsName = usernamePropsName;
    }

    public void setPasswordPropsName(String passwordPropsName) {
        this.passwordPropsName = passwordPropsName;
    }

    @Autowired(required = false)
    public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
        this.urlPathHelper = urlPathHelper;
    }

}