package org.apache.cloudstack.storage.datastore.provider;

import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreLifeCycle;
import org.apache.cloudstack.engine.subsystem.api.storage.HypervisorHostListener;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreDriver;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreProvider;
import org.apache.cloudstack.storage.datastore.driver.NetAppPrimaryDataStoreDriverImpl;
import org.apache.cloudstack.storage.datastore.lifecycle.NetAppPrimaryDataStoreLifeCycleImpl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NetAppPrimaryDataStoreProviderImpl implements PrimaryDataStoreProvider {

    protected PrimaryDataStoreDriver driver;
    protected HypervisorHostListener listener;
    protected DataStoreLifeCycle lifecycle;

    NetAppPrimaryDataStoreProviderImpl() {
    }

    @Override
    public String getName() {
        return "NetApp Primary";
    }

    @Override
    public DataStoreLifeCycle getDataStoreLifeCycle() {
        return lifecycle;
    }

    @Override
    public boolean configure(Map<String, Object> params) {
        lifecycle = ComponentContext.inject(NetAppPrimaryDataStoreLifeCycleImpl.class);
        driver = ComponentContext.inject(NetAppPrimaryDataStoreDriverImpl.class);
        listener = ComponentContext.inject(DefaultHostListener.class);
        return true;
    }

    @Override
    public PrimaryDataStoreDriver getDataStoreDriver() {
        return driver;
    }

    @Override
    public HypervisorHostListener getHostListener() {
        return listener;
    }

    @Override
    public Set<DataStoreProviderType> getTypes() {
        Set<DataStoreProviderType> types = new HashSet<DataStoreProviderType>();
        types.add(DataStoreProviderType.PRIMARY);
        return types;
    }
}
