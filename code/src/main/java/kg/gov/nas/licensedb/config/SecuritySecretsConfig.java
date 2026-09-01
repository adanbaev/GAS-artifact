package kg.gov.nas.licensedb.config;

import kg.gov.nas.licensedb.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class SecuritySecretsConfig {

    @Value("${security.signature.secret:}")
    private String signatureSecret;

    @Value("${security.integrity.chain-secret:}")
    private String chainSecret;

    @PostConstruct
    public void init() {
        SecurityUtil.setSignatureSecret(signatureSecret);
        SecurityUtil.setChainSecret(chainSecret);
    }
}
