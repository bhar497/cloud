//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package org.apache.cloudstack.ca;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import com.cloud.alert.AlertManager;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.MacAddress;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.framework.ca.CAProvider;
import org.apache.cloudstack.framework.ca.Certificate;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.ScopedConfigStorage;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;
import org.apache.cloudstack.framework.config.impl.ConfigurationVO;
import org.apache.cloudstack.utils.security.CertUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.certificate.CrlVO;
import com.cloud.certificate.dao.CrlDao;
import com.cloud.host.Host;
import com.cloud.host.dao.HostDao;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class CAManagerImplTest {

    @Mock
    private HostDao hostDao;
    @Mock
    private CrlDao crlDao;
    @Mock
    private AgentManager agentManager;
    @Mock
    private CAProvider caProvider;
    @Mock
    private ConfigDepotImpl configDepot;
    @Mock
    private ConfigurationDao configDao;
    @Mock
    private ScopedConfigStorage clusterStorage;
    @Mock
    private AlertManager alertManager;

    private CAManagerImpl caManager;
    private CAManagerImpl.CABackgroundTask task;

    private void addField(final CAManagerImpl provider, final String name, final Object o) throws IllegalAccessException, NoSuchFieldException {
        Field f = CAManagerImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(provider, o);
    }

    @Before
    public void setUp() throws Exception {
        caManager = new CAManagerImpl();
        addField(caManager, "crlDao", crlDao);
        addField(caManager, "hostDao", hostDao);
        addField(caManager, "agentManager", agentManager);
        addField(caManager, "configuredCaProvider", caProvider);
        addField(caManager, "alertManager", alertManager);
        // This is because it is using a static variable in the class. So each test contaminates the next.
        caManager.getAlertMap().clear();

        Mockito.when(caProvider.getProviderName()).thenReturn("root");
        caManager.setCaProviders(Collections.singletonList(caProvider));
        ConfigKey.init(configDepot);
        setupHost();
        task = new CAManagerImpl.CABackgroundTask(caManager, hostDao);
        Mockito.when(configDepot.global()).thenReturn(configDao);
    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(crlDao);
        Mockito.reset(agentManager);
        Mockito.reset(caProvider);
    }

    @Test(expected = ServerApiException.class)
    public void testIssueCertificateThrowsException() throws Exception {
        caManager.issueCertificate(null, null, null, 1, null);
    }

    @Test
    public void testIssueCertificate() throws Exception {
        caManager.issueCertificate(null, Collections.singletonList("domain.example"), null, 1, null);
        Mockito.verify(caProvider, Mockito.times(1)).issueCertificate(Mockito.anyList(), Mockito.anyList(), Mockito.anyInt());
        Mockito.verify(caProvider, Mockito.times(0)).issueCertificate(Mockito.anyString(), Mockito.anyList(), Mockito.anyList(), Mockito.anyInt());
    }

    @Test
    public void testRevokeCertificate() throws Exception {
        final CrlVO crl = new CrlVO(CertUtils.generateRandomBigInt(), "some.domain", "some-uuid");
        Mockito.when(crlDao.revokeCertificate(Mockito.any(BigInteger.class), Mockito.anyString())).thenReturn(crl);
        Mockito.when(caProvider.revokeCertificate(Mockito.any(BigInteger.class), Mockito.anyString())).thenReturn(true);
        Assert.assertTrue(caManager.revokeCertificate(crl.getCertSerial(), crl.getCertCn(), null));
        Mockito.verify(caProvider, Mockito.times(1)).revokeCertificate(Mockito.any(BigInteger.class), Mockito.anyString());
    }

    @Test
    public void testProvisionCertificate() throws Exception {
        final Host host = Mockito.mock(Host.class);
        Mockito.when(host.getPrivateIpAddress()).thenReturn("1.2.3.4");
        final KeyPair keyPair = CertUtils.generateRandomKeyPair(1024);
        final X509Certificate certificate = CertUtils.generateV3Certificate(null, keyPair, keyPair.getPublic(), "CN=ca", "SHA256withRSA", 365, null, null);
        Mockito.when(caProvider.issueCertificate(Mockito.anyString(), Mockito.anyList(), Mockito.anyList(), Mockito.anyInt())).thenReturn(new Certificate(certificate, null, Collections.singletonList(certificate)));
        Mockito.when(agentManager.send(Mockito.anyLong(), Mockito.any(SetupKeyStoreCommand.class))).thenReturn(new SetupKeystoreAnswer("someCsr"));
        Mockito.when(agentManager.reconnect(Mockito.anyLong())).thenReturn(true);
        Assert.assertTrue(caManager.provisionCertificate(host, true, null));
        Mockito.verify(agentManager, Mockito.times(2)).send(Mockito.anyLong(), Mockito.any(Answer.class));
        Mockito.verify(agentManager, Mockito.times(1)).reconnect(Mockito.anyLong());
    }

    @Test
    public void testAutoRenewWarning() throws Exception {
        setupCertMap(4);
        setupAllConfigForRenewal("2", "3", "true", "365");

        task.runInContext();

        verifyAlerts("Certificate expiring soon for.*", "Certificate is going to expire for.*It will auto renew on.*");
    }

    @Test
    public void testExpirationWarning() throws Exception {
        setupCertMap(4);
        setupAllConfigForRenewal("2", "3", "false", "365");

        task.runInContext();

        verifyAlerts("Certificate expiring soon for.*", "Certificate is going to expire for.*Auto renewing is not enabled.");
    }

    @Test
    public void testExpirationAlert() throws Exception {
        X509Certificate cert = setupCertMap(4);
        setupAllConfigForRenewal("5", "3", "false", "365");

        task.runInContext();

        verifyAlerts("Certificate expiring soon for.*", String.format("Certificate is going to expire for.*Please manually renew it since auto-renew is disabled. It is not valid after %s.", cert.getNotAfter()));
    }

    @Test
    public void testAutoRenew() throws Exception {
        setupCertMap(4);
        setupAllConfigForRenewal("5", "3", "true", "365");

        final KeyPair keyPair = CertUtils.generateRandomKeyPair(1024);
        final X509Certificate certificate = CertUtils.generateV3Certificate(null, keyPair, keyPair.getPublic(), "CN=ca", "SHA256withRSA", 365, null, null);
        Mockito.when(caProvider.issueCertificate(Mockito.anyString(), Mockito.anyList(), Mockito.anyList(), Mockito.anyInt())).thenReturn(new Certificate(certificate, null, Collections.singletonList(certificate)));
        Mockito.when(agentManager.send(anyLong(), any(SetupKeyStoreCommand.class))).thenReturn(new SetupKeystoreAnswer("foo"));
        Mockito.when(agentManager.reconnect(Mockito.anyLong())).thenReturn(true);

        task.runInContext();

        verifyAlerts("Certificate auto-renewal succeeded for host.*", "Certificate auto-renew succeeded for.*");
    }

    @Test
    public void testFailedAutoRenew() throws Exception {
        X509Certificate cert = setupCertMap(4);

        setupAllConfigForRenewal("5", "3", "true", "365");
        final KeyPair keyPair = CertUtils.generateRandomKeyPair(1024);
        final X509Certificate certificate = CertUtils.generateV3Certificate(null, keyPair, keyPair.getPublic(), "CN=ca", "SHA256withRSA", 365, null, null);
        Mockito.when(caProvider.issueCertificate(Mockito.anyString(), Mockito.anyList(), Mockito.anyList(), Mockito.anyInt())).thenReturn(new Certificate(certificate, null, Collections.singletonList(certificate)));
        Mockito.when(agentManager.send(anyLong(), any(SetupKeyStoreCommand.class))).thenReturn(new SetupKeystoreAnswer(""));
        Mockito.when(agentManager.reconnect(Mockito.anyLong())).thenReturn(true);

        task.runInContext();

        verifyAlerts("Certificate auto-renewal failed for host.*", String.format("Certificate is going to expire for.* Auto-renewal failed to renew the certificate, please renew it manually. It is not valid after %s.", cert.getNotAfter()));
    }

    @Test
    public void testFailedAutoRenewError() throws Exception {
        X509Certificate cert = setupCertMap(4);

        setupAllConfigForRenewal("5", "3", "true", "365");
        final X509Certificate certificate = getX509Certificate();
        Mockito.when(caProvider.issueCertificate(Mockito.anyString(), Mockito.anyList(), Mockito.anyList(), Mockito.anyInt())).thenReturn(new Certificate(certificate, null, Collections.singletonList(certificate)));
        Mockito.when(agentManager.send(anyLong(), any(SetupKeyStoreCommand.class))).thenReturn(new SetupKeystoreAnswer("foo"));
        Mockito.when(agentManager.send(anyLong(), any(SetupCertificateCommand.class))).thenThrow(new CloudRuntimeException("Unable to setup cert"));
        Mockito.when(agentManager.reconnect(Mockito.anyLong())).thenReturn(true);

        task.runInContext();

        verifyAlerts("Certificate auto-renewal failed for host.*", String.format("Certificate is going to expire for.* Error in auto-renewal, failed to renew the certificate, please renew it manually. It is not valid after %s.", cert.getNotAfter()));
    }

    private static X509Certificate getX509Certificate() throws NoSuchProviderException, NoSuchAlgorithmException, IOException, CertificateException, InvalidKeyException, SignatureException, OperatorCreationException {
        final KeyPair keyPair = CertUtils.generateRandomKeyPair(1024);
        final X509Certificate certificate = CertUtils.generateV3Certificate(null, keyPair, keyPair.getPublic(), "CN=ca", "SHA256withRSA", 365, null, null);
        return certificate;
    }

    private void setupHost() {
        final HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getPrivateIpAddress()).thenReturn("1.2.3.4");
        Mockito.when(hostDao.findByIp("1.2.3.4")).thenReturn(host);
        Mockito.when(host.getManagementServerId()).thenReturn(MacAddress.getMacAddress().toLong());
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        Mockito.when(host.getClusterId()).thenReturn(1L);
    }

    private X509Certificate setupCertMap(int validityDays) throws NoSuchProviderException, NoSuchAlgorithmException, IOException, CertificateException, InvalidKeyException, SignatureException, OperatorCreationException {
        Map<String, X509Certificate> certsMap = caManager.getActiveCertificatesMap();
        final KeyPair keyPair = CertUtils.generateRandomKeyPair(1024);
        final X509Certificate certificate = CertUtils.generateV3Certificate(null, keyPair, keyPair.getPublic(), "CN=ca", "SHA256withRSA", validityDays, null, null);
        certsMap.put("1.2.3.4", certificate);
        return certificate;
    }

    void setupConfig(ConfigKey<?> key, String value) {
        Mockito.when(configDepot.scoped(key)).thenReturn(clusterStorage);
        Mockito.when(clusterStorage.getConfigValue(1, key)).thenReturn(value);
        Mockito.when(configDao.findById(key.key())).thenReturn(new ConfigurationVO("testing", key.key(), "testing", key.key(), value, key.description()));
    }

    private void verifyAlerts(String subjectRegex, String messageRegex) {
        Mockito.verify(alertManager, Mockito.times(1)).sendAlert(eq(AlertManager.AlertType.ALERT_TYPE_CA_CERT), eq(0L), eq(0L),
                Mockito.matches(subjectRegex),
                Mockito.matches(messageRegex));
    }

    private void setupAllConfigForRenewal(String alertDays, String warningDays, String autoRenew, String validDays) {
        setupConfig(CAManager.CertExpiryAlertPeriod, alertDays);
        setupConfig(CAManager.CertExpiryWarningPeriod, warningDays);
        setupConfig(CAManager.AutomaticCertRenewal, autoRenew);
        setupConfig(CAManager.CertValidityPeriod, validDays);
    }
}