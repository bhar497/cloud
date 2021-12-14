package org.apache.cloudstack.storage.datastore.lifecycle;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.ModifyStoragePoolAnswer;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StorageManagerImpl;
import com.cloud.storage.StoragePoolHostVO;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.utils.PropertiesUtil;
import org.apache.cloudstack.engine.subsystem.api.storage.ClusterScope;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProvider;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProviderManager;
import org.apache.cloudstack.engine.subsystem.api.storage.HypervisorHostListener;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreLifeCycle;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreParameters;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.datastore.provider.DefaultHostListener;
import org.apache.cloudstack.storage.volume.datastore.PrimaryDataStoreHelper;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NetAppPrimaryDataStoreLifeCycleImplTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @InjectMocks
    PrimaryDataStoreLifeCycle _netAppPrimaryDataStoreLifeCycle = new NetAppPrimaryDataStoreLifeCycleImpl();
    @Spy
    @InjectMocks
    StorageManager storageMgr = new StorageManagerImpl();
    @Mock
    ResourceManager _resourceMgr;
    @Mock
    AgentManager agentMgr;
    @Mock
    DataStoreManager _dataStoreMgr;
    @Mock
    DataStoreProviderManager _dataStoreProviderMgr;
    @Spy
    @InjectMocks
    HypervisorHostListener hostListener = new DefaultHostListener();
    @Mock
    StoragePoolHostDao storagePoolHostDao;
    @Mock
    PrimaryDataStore store;
    @Mock
    DataStoreProvider dataStoreProvider;
    @Mock
    ModifyStoragePoolAnswer answer;
    @Mock
    StoragePoolInfo info;
    @Mock
    PrimaryDataStoreDao primaryStoreDao;
    @Mock
    StoragePoolVO storagePool;
    @Mock
    PrimaryDataStoreHelper primaryDataStoreHelper;
    DsInfoBuilder dsInfo = new DsInfoBuilder();

    @Before
    public void initMocks() {
        File file = PropertiesUtil.findConfigFile("log4j-cloud.xml");
        DOMConfigurator.configureAndWatch(file.getAbsolutePath());
        MockitoAnnotations.initMocks(this);

        List<HostVO> hostList = new ArrayList<HostVO>();
        HostVO host1 = new HostVO(1L, "aa01", Host.Type.Routing, "192.168.1.1", "255.255.255.0", null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID().toString(), Status.Up, "1.0", null, null, 1L, null, 0, 0, "aa", 0, Storage.StoragePoolType.NetworkFilesystem);
        HostVO host2 = new HostVO(1L, "aa02", Host.Type.Routing, "192.168.1.1", "255.255.255.0", null, null, null, null, null, null, null, null, null, null,
                UUID.randomUUID().toString(), Status.Up, "1.0", null, null, 1L, null, 0, 0, "aa", 0, Storage.StoragePoolType.NetworkFilesystem);

        host1.setResourceState(ResourceState.Enabled);
        host2.setResourceState(ResourceState.Disabled);
        hostList.add(host1);
        hostList.add(host2);

        when(_dataStoreMgr.getDataStore(anyLong(), eq(DataStoreRole.Primary))).thenReturn(store);
        when(store.getPoolType()).thenReturn(Storage.StoragePoolType.NetworkFilesystem);
        when(store.isShared()).thenReturn(true);
        when(store.getName()).thenReturn("newPool");

        when(_dataStoreProviderMgr.getDataStoreProvider(anyString())).thenReturn(dataStoreProvider);
        when(dataStoreProvider.getName()).thenReturn("default");
        ((StorageManagerImpl) storageMgr).registerHostListener("default", hostListener);

        when(_resourceMgr.listAllUpHosts(eq(Host.Type.Routing), anyLong(), anyLong(), anyLong())).thenReturn(hostList);
        when(agentMgr.easySend(anyLong(), Mockito.any(ModifyStoragePoolCommand.class))).thenReturn(answer);
        when(answer.getResult()).thenReturn(true);
        when(answer.getPoolInfo()).thenReturn(info);

        when(info.getLocalPath()).thenReturn("/mnt/1");
        when(info.getCapacityBytes()).thenReturn(0L);
        when(info.getAvailableBytes()).thenReturn(0L);

        when(storagePoolHostDao.findByPoolHost(anyLong(), anyLong())).thenReturn(null);
        when(primaryStoreDao.findById(anyLong())).thenReturn(storagePool);
        when(primaryStoreDao.update(anyLong(), Mockito.any(StoragePoolVO.class))).thenReturn(true);
        when(primaryDataStoreHelper.attachCluster(Mockito.any(DataStore.class))).thenReturn(null);
    }

    @Test
    public void testAttachCluster() throws Exception {
        _netAppPrimaryDataStoreLifeCycle.attachCluster(store, new ClusterScope(1L, 1L, 1L));
        verify(storagePoolHostDao, times(2)).persist(Mockito.any(StoragePoolHostVO.class));

    }

    @Test
    public void testPrimaryDataStoreCreatedWhenInitializedWithValidParameters() {
        validInitialize();
        verify(primaryDataStoreHelper, times(1)).createPrimaryDataStore(Mockito.isA(PrimaryDataStoreParameters.class));
    }

    @Test
    public void testClusterIdRequiresPodId() {
        setupExceptionRule("Cluster id requires pod id");
        initialize(dsInfo.validUrl().validClusterId());
    }

    @Test
    public void testUrlIsRequired() {
        setupExceptionRule("URL is required");
        initialize(dsInfo);
    }

    @Test
    public void testUrlIsNfs() {
        setupExceptionRule("URL must be nfs scheme");
        initialize(dsInfo.url("http://localhost"));
    }

    @Test
    public void testUrlHasHost() {
        setupExceptionRule("URL must have host");
        initialize(dsInfo.url("nfs:///path"));
    }

    @Test
    public void testUrlHasPath() {
        setupExceptionRule("URL must have path");
        initialize(dsInfo.url("nfs://host"));
    }

    @Test
    public void testDetailsPassedToParameters() {
        Map<String, String> details = new HashMap<>();
        initialize(dsInfo.valid().details(details));
        assertEquals(details, verifyCreateParams().getDetails());
    }

    @Test
    public void testTypeIsNFS() {
        validInitialize();
        PrimaryDataStoreParameters value = verifyCreateParams();
        assertEquals(Storage.StoragePoolType.NetworkFilesystem, value.getType());
    }

    @Test
    public void testHostisHost() {
        validInitialize();
        assertEquals("host", verifyCreateParams().getHost());
    }

    @Test
    public void testPathIsPath() {
        validInitialize();
        assertEquals("/path", verifyCreateParams().getPath());
    }

    @Test
    public void testPortIs2049() {
        validInitialize();
        assertEquals(2049, verifyCreateParams().getPort());
    }

    @Test
    public void testExistingUuidIsUsedIfPresent() {
        initialize(dsInfo.valid().uuid("abc"));
        assertEquals("abc", verifyCreateParams().getUuid());
    }

    @Test
    public void testGenerateUuidFromHostAndPathIfMissing() {
        String uuid = UUID.nameUUIDFromBytes(("host" + "/path").getBytes()).toString();
        initialize(dsInfo.valid().uuid(null));
        assertEquals(uuid, verifyCreateParams().getUuid());
    }

    @Test
    public void testZoneIdIsPassed() {
        validInitialize();
        assertEquals(3L, (long) verifyCreateParams().getZoneId());
    }

    @Test
    public void testPodIdIsPassed() {
        validInitialize();
        assertEquals(2L, (long) verifyCreateParams().getPodId());
    }

    @Test
    public void testClusterIdIsPassed() {
        validInitialize();
        assertEquals(1L, (long) verifyCreateParams().getClusterId());
    }

    @Test
    public void testProviderNameIsPassed() {
        validInitialize();
        assertEquals("NetApp", verifyCreateParams().getProviderName());
    }

    private DataStore validInitialize() {
        return initialize(dsInfo.valid());
    }

    private DataStore initialize(DsInfoBuilder valid) {
        return _netAppPrimaryDataStoreLifeCycle.initialize(valid.b());
    }

    private PrimaryDataStoreParameters verifyCreateParams() {
        ArgumentCaptor<PrimaryDataStoreParameters> captor = ArgumentCaptor.forClass(PrimaryDataStoreParameters.class);
        verify(primaryDataStoreHelper).createPrimaryDataStore(captor.capture());
        return captor.getValue();
    }

    private void setupExceptionRule(String message) {
        exceptionRule.expect(InvalidParameterValueException.class);
        exceptionRule.expectMessage(message);
    }

    static class DsInfoBuilder {
        String url;
        Long clusterId;
        Long podId;
        Map<String, String> details;
        String uuid;
        Long zoneId;
        String providerName;

        DsInfoBuilder() {

        }

        DsInfoBuilder(String url, Long clusterId, Long podId) {
            this.url = url;
            this.clusterId = clusterId;
            this.podId = podId;
        }

        Map<String, Object> b() {
            return new HashMap<String, Object>() {{
                put("url", url);
                put("clusterId", clusterId);
                put("podId", podId);
                put("details", details);
                put("uuid", uuid);
                put("zoneId", zoneId);
                put("providerName", providerName);
            }};
        }

        DsInfoBuilder valid() {
            validUrl();
            validClusterId();
            validPodId();
            validDetails();
            validUuid();
            validZoneId();
            validProviderName();
            return this;
        }

        DsInfoBuilder url(String url) {
            this.url = url;
            return this;
        }

        DsInfoBuilder validUrl() {
            this.url = "nfs://host/path";
            return this;
        }

        DsInfoBuilder clusterId(Long clusterId) {
            this.clusterId = clusterId;
            return this;
        }

        DsInfoBuilder validClusterId() {
            this.clusterId = 1L;
            return this;
        }

        DsInfoBuilder podId(Long podId) {
            this.podId = podId;
            return this;
        }

        DsInfoBuilder validPodId() {
            this.podId = 2L;
            return this;
        }

        DsInfoBuilder details(Map<String, String> details) {
            this.details = details;
            return this;
        }

        DsInfoBuilder validDetails() {
            this.details = new HashMap<>();
            return this;
        }

        DsInfoBuilder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        DsInfoBuilder validUuid() {
            this.uuid = UUID.randomUUID().toString();
            return this;
        }

        DsInfoBuilder zoneId(Long zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        DsInfoBuilder validZoneId() {
            this.zoneId = 3L;
            return this;
        }

        DsInfoBuilder providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }

        DsInfoBuilder validProviderName() {
            this.providerName = "NetApp";
            return this;
        }
    }
}
