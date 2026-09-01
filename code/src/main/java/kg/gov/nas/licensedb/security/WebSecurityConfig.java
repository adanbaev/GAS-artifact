package kg.gov.nas.licensedb.security;

import kg.gov.nas.licensedb.service.MyUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private MyUserDetailsService userDetailsService;

    @Autowired
    private TrbacPerRequestFilter trbacPerRequestFilter;

    @Autowired
    private MustChangePasswordFilter mustChangePasswordFilter;

    @Autowired
    private LoginSuccessHandler loginSuccessHandler;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .userDetailsService(userDetailsService)
            .passwordEncoder(bCryptPasswordEncoder);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .addFilterAfter(trbacPerRequestFilter, SecurityContextPersistenceFilter.class)
            .addFilterAfter(mustChangePasswordFilter, TrbacPerRequestFilter.class)

            .authorizeRequests()
            .antMatchers("/login", "/forgot-password")
            .permitAll()

            .antMatchers("/registration").hasRole("ADMIN")
            .antMatchers("/admin/user/**").hasRole("ADMIN")
            .antMatchers("/admin/logs/**").hasAnyRole("ADMIN", "AUDITOR")
            .antMatchers("/admin/security-events/**").hasAnyRole("ADMIN", "AUDITOR")
            .antMatchers("/admin/incidents/**").hasAnyRole("ADMIN", "AUDITOR")
            .antMatchers("/admin/integrity/**").hasAnyRole("ADMIN", "AUDITOR")
            .antMatchers("/admin/**").hasRole("ADMIN")

            .anyRequest()
            .authenticated()
            .and()

            .formLogin()
            .loginPage("/login")
            .failureUrl("/login?error=true")
            .usernameParameter("user_name")
            .passwordParameter("password")
            .successHandler(loginSuccessHandler)
            .permitAll()
            .and()

            .logout()
            .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
            .logoutSuccessUrl("/login")
            .and()

            .exceptionHandling(exception -> exception
                .accessDeniedPage("/access-denied")
            );
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web
            .ignoring()
            .antMatchers(
                "/resources/**",
                "/static/**",
                "/css/**",
                "/js/**",
                "/img/**",
                "/fonts/**",
                "/font-awesome/**",
                "/favicon.ico"
            );
    }
}
