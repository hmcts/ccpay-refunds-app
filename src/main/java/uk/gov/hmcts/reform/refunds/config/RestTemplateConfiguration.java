package uk.gov.hmcts.reform.refunds.config;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;

@Configuration
public class RestTemplateConfiguration {

    @Bean(name = {"paymentsHttpClient", "serviceTokenParserHttpClient", "userTokenParserHttpClient"})
    public CloseableHttpClient paymentsHttpClient() {
        return HttpClients.custom()
            .useSystemProperties()
            .build();
    }

    @Bean("restTemplateIdam")
    public RestTemplate restTemplateIdam() {
        return  new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    @Bean("restTemplatePayment")
    public RestTemplate restTemplatePayment() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    @Bean("restTemplateLiberata")
    public RestTemplate restTemplateLiberata() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException,
        CertificateException, IOException {
        SSLContext sslContext = new SSLContextBuilder()
            .loadTrustMaterial(new URL("file:src/main/resources/liberatadigicert.p12"),
                               "liberatadigicert".toCharArray()).build();
        SSLConnectionSocketFactory sslConFactory = new SSLConnectionSocketFactory(sslContext);
        ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(
            HttpClients.custom().setSSLSocketFactory(sslConFactory).build());
        return new RestTemplate(requestFactory);
    }

}
