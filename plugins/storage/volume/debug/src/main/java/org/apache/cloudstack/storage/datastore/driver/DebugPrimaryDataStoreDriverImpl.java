package org.apache.cloudstack.storage.datastore.driver;

import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.VolumeDetailVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.utils.script.Script;
import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.CreateCmdResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreCapabilities;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreDriver;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.command.CreateObjectAnswer;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.to.SnapshotObjectTO;
import org.apache.log4j.Logger;
import org.joda.time.Duration;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugPrimaryDataStoreDriverImpl extends CloudStackPrimaryDataStoreDriverImpl {
    private static final Logger LOGGER = Logger.getLogger(DebugPrimaryDataStoreDriverImpl.class);

    @Inject
    private PrimaryDataStoreDao storagePoolDao;

    @Inject private VolumeDetailsDao volumeDetailsDao;
    @Inject private VMTemplatePoolDao tmpltPoolDao;

    @Override
    public Map<String, String> getCapabilities() {
        Map<String, String> mapCapabilities = new HashMap<>();

        mapCapabilities.put(DataStoreCapabilities.VOLUME_SNAPSHOT_QUIESCEVM.toString(), "false");
        mapCapabilities.put(DataStoreCapabilities.STORAGE_SYSTEM_SNAPSHOT.toString(), Boolean.TRUE.toString());
        mapCapabilities.put(DataStoreCapabilities.CAN_CREATE_VOLUME_FROM_SNAPSHOT.toString(), Boolean.TRUE.toString());
        mapCapabilities.put(DataStoreCapabilities.CAN_CREATE_VOLUME_FROM_VOLUME.toString(), Boolean.TRUE.toString());
        mapCapabilities.put(DataStoreCapabilities.CAN_REVERT_VOLUME_TO_SNAPSHOT.toString(), Boolean.TRUE.toString());

        return mapCapabilities;
    }

    @Override
    public void takeSnapshot(SnapshotInfo snapshot, AsyncCompletionCallback<CreateCmdResult> callback) {
        VolumeInfo volumeInfo = snapshot.getBaseVolume();
        VolumeVO volumeVO = volumeDao.findById(volumeInfo.getId());

        CreateCmdResult result = new CreateCmdResult(null, null);

        long storagePoolId = volumeVO.getPoolId();
        StoragePoolVO storagePool = storagePoolDao.findById(storagePoolId);

        // Get info about the volume and snapshot
        String path = storagePool.getPath();
        String volumePath = volumeInfo.getPath();
        String snapshotPath = volumePath + "-" + snapshot.getUuid();

        // File operations
        File volumeFile = new File(path, volumePath);
        File snapshotFile = new File(path, snapshotPath);

        // Need to copy the volume
        Script command = new Script("cp", Duration.standardSeconds(300), LOGGER);
        // Lots of assumptions here since the mgmt server is also the NFS server
        command.add(volumeFile.getAbsolutePath());
        command.add(snapshotFile.getAbsolutePath());
        String cmdResult = command.execute();
        // End file operations

        LOGGER.info("Copy returned with exit code: " + command.getExitValue());

        SnapshotObjectTO snapshotObjectTo = (SnapshotObjectTO) snapshot.getTO();

        // Update the object path
        snapshotObjectTo.setPath(snapshotFile.getAbsolutePath());

        // Complete the process
        CreateObjectAnswer createObjectAnswer = new CreateObjectAnswer(snapshotObjectTo);
        result.setAnswer(createObjectAnswer);
        result.setResult(cmdResult);
        callback.complete(result);
    }

//    @Override
//    public void deleteAsync(DataStore dataStore, DataObject data, AsyncCompletionCallback<CommandResult> callback) {
//        if (dataStore instanceof PrimaryDataStore && data instanceof SnapshotObject) {
//            PrimaryDataStore primaryStore = (PrimaryDataStore)dataStore;
//            SnapshotObject snapshot = (SnapshotObject) data;
//            String snapshotFile = snapshot.getPath();
//            LOGGER.debug("Attempting to delete snapshot " + snapshotFile);
//            CommandResult result = new CommandResult();
//            try {
//                File file = new File(primaryStore.getPath(), snapshotFile);
//                if (file.exists()) {
//                    if (!file.delete()) {
//                        String message = "Unable to delete file " + snapshotFile + " on primary storage";
//                        LOGGER.error(message);
//                        result.setResult(message);
//                    }
//                }
//            } catch (Exception ex) {
//                LOGGER.error("Unable to destory snapshot" + data.getId(), ex);
//                result.setResult(ex.toString());
//            }
//            callback.complete(result);
//        } else {
//            super.deleteAsync(dataStore, data, callback);
//        }
//    }

    @Override
    public void revertSnapshot(SnapshotInfo snapshot, SnapshotInfo snapshotOnPrimaryStore, AsyncCompletionCallback<CommandResult> callback) {
        VolumeInfo volumeInfo = snapshot.getBaseVolume();
        PrimaryDataStore store = (PrimaryDataStore) volumeInfo.getDataStore();
        CommandResult result = new CommandResult();
        try {
            File volumeFile = new File(store.getPath(), volumeInfo.getPath());
            File snapshotFile = new File(store.getPath(), snapshot.getPath());

            Script command = new Script("sudo cp", Duration.standardSeconds(300), LOGGER);
            command.add(snapshotFile.getAbsolutePath());
            command.add(volumeFile.getAbsolutePath());
            result.setResult(command.execute());
        } catch (Exception ex) {
            LOGGER.error("Unable to revert snapshot " + snapshot.getPath());
            result.setResult(ex.toString());
        }
        callback.complete(result);
    }

    @Override
    public boolean canCopy(DataObject srcData, DataObject destData) {
        LOGGER.debug("DEBUG STORE Can Copy Check\nSrc: " + this.dataObjectToString(srcData) + "\nDest: " + this.dataObjectToString(destData));
        return super.canCopy(srcData, destData);
    }

    @Override
    public void copyAsync(DataObject srcdata, DataObject destData, AsyncCompletionCallback<CopyCommandResult> callback) {
        LOGGER.debug("DEBUG STORE Copy Async\nSrc: " + this.dataObjectToString(srcdata) + "\nDest: " + this.dataObjectToString(destData));
        super.copyAsync(srcdata, destData, callback);
    }

    @Override
    public void deleteAsync(DataStore dataStore, DataObject data, AsyncCompletionCallback<CommandResult> callback) {
        LOGGER.debug("DEBUG STORE Delete Async\nStore: " + this.dataStoreToString(dataStore) + "\nData: " + this.dataObjectToString(data));
        super.deleteAsync(dataStore, data, callback);
    }

    @Override
    public void createAsync(DataStore dataStore, DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        LOGGER.debug("DEBUG STORE Create Async\nStore: " + this.dataStoreToString(dataStore) + "\nData: " + this.dataObjectToString(data));
        List<VolumeDetailVO> details = volumeDetailsDao.listDetails(data.getId());
        long templateId = 0;
        for(VolumeDetailVO detail : details) {
            LOGGER.debug("DEBUG STORE Volume Detail: " + detail.getName() + " : " + detail.getValue());
            if ("cloneOfTemplate".equals(detail.getName())) {
                templateId = new Long(detail.getValue());
            }
        }

        if (templateId > 0) {
            VMTemplateStoragePoolVO templatePoolRef = tmpltPoolDao.findByPoolTemplate(dataStore.getId(), templateId);
            LOGGER.debug("DEBUG STORE Template Path: " + templatePoolRef.getLocalDownloadPath());
        }
        super.createAsync(dataStore, data, callback);
    }

    @Override
    public void resize(DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        LOGGER.debug("DEBUG STORE Resize\nData: " + this.dataObjectToString(data));
        super.resize(data, callback);
    }

    private String dataObjectToString(DataObject obj) {
        DataStore store = obj.getDataStore();

        return "DataObject: Type: " + obj.getType() +
                " ID: " + obj.getId() +
//                " URI " + obj.getUri() +
                " UUID: " + obj.getUuid() +
                " Class: " + obj.getClass() +
                (store != null ? "\n" + this.dataStoreToString(store) : "") +
                "";
    }

    private String dataStoreToString(DataStore store) {
        DataStoreDriver driver = store.getDriver();
        return "DataStore Name: " + store.getName() +
                " ID: " + store.getId() +
//                " URI: " + store.getUri() +
                " UUID " + store.getUuid() +
                " Role: " + store.getRole() +
                " Scope: " + store.getScope() +
                " Class: " + store.getClass() +
                (driver != null ? " Driver: " + driver.getClass() : "") +
                "";
    }
}
