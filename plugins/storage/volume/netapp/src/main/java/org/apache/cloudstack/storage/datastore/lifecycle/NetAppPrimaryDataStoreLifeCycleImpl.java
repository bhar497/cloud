package org.apache.cloudstack.storage.datastore.lifecycle;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.storage.Storage;
import com.cloud.utils.UriUtils;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreParameters;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;

public class NetAppPrimaryDataStoreLifeCycleImpl extends CloudStackPrimaryDataStoreLifeCycleImpl {

    @SuppressWarnings("unchecked")
    @Override
    public DataStore initialize(Map<String, Object> dsInfos) {
        Long clusterId = (Long)dsInfos.get("clusterId");
        Long podId = (Long)dsInfos.get("podId");
        String url = (String)dsInfos.get("url");

        if (clusterId != null && podId == null) {
            throw new InvalidParameterValueException("Cluster id requires pod id");
        }

        if (url == null) {
            throw new InvalidParameterValueException("URL is required");
        }

        String uriHost = null;
        String uriPath = null;
        try {
            URI uri = new URI(UriUtils.encodeURIComponent(url));
            if (!uri.getScheme().equalsIgnoreCase("nfs")) {
                throw new InvalidParameterValueException("URL must be nfs scheme");
            }

            uriHost = uri.getHost();
            uriPath = uri.getPath();

            if (uriHost == null) {
                throw new InvalidParameterValueException("URL must have host");
            }

            if (uriPath == null || uriPath.trim().isEmpty()) {
                throw new InvalidParameterValueException("URL must have path");
            }

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        PrimaryDataStoreParameters params = new PrimaryDataStoreParameters();
        params.setDetails((Map<String, String>)dsInfos.get("details"));
        params.setType(Storage.StoragePoolType.NetworkFilesystem);
        params.setHost(uriHost);
        params.setPath(uriPath);
        params.setPort(2049);
        String existingUuid = (String) dsInfos.get("uuid");
        String uuid;

        if (existingUuid != null) {
            uuid = existingUuid;
        } else {
            uuid = UUID.nameUUIDFromBytes((uriHost + uriPath).getBytes()).toString();
        }

        params.setUuid(uuid);
        params.setZoneId((Long)dsInfos.get("zoneId"));
        params.setPodId((Long)dsInfos.get("podId"));
        params.setClusterId((Long)dsInfos.get("clusterId"));
        params.setProviderName((String)dsInfos.get("providerName"));

        return dataStoreHelper.createPrimaryDataStore(params);
    }
}
