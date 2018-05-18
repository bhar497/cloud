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

package org.apache.cloudstack.backup.dao;

import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.response.BackupResponse;
import org.apache.cloudstack.backup.BackupVO;
import org.apache.cloudstack.backup.Backup;
import org.apache.commons.collections.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class BackupDaoImpl extends GenericDaoBase<BackupVO, Long> implements BackupDao {

    @Inject
    AccountDao accountDao;

    @Inject
    UserDao userDao;

    @Inject
    VMInstanceDao vmInstanceDao;

    @Inject
    VolumeDao volumeDao;

    private SearchBuilder<BackupVO> backupSearch;

    public BackupDaoImpl() {
    }

    @PostConstruct
    protected void init() {
        backupSearch = createSearchBuilder();
        backupSearch.and("vm_id", backupSearch.entity().getVMId(), SearchCriteria.Op.EQ);
        backupSearch.and("user_id", backupSearch.entity().getUserId(), SearchCriteria.Op.EQ);
        backupSearch.done();
    }
    @Override
    public List<Backup> listByVmId(Long vmId) {
        SearchCriteria<BackupVO> sc = backupSearch.create();
        sc.setParameters("vm_id", vmId);
        return new ArrayList<>(listBy(sc));
    }

    @Override
    public List<Backup> listByUserId(Long userId) {
        SearchCriteria<BackupVO> sc = backupSearch.create();
        sc.setParameters("user_id", userId);
        return new ArrayList<>(listBy(sc));
    }

    @Override
    public BackupResponse newBackupResponse(Backup backup) {
        AccountVO account = accountDao.findById(backup.getAccountId());
        UserVO user = userDao.findById(backup.getUserId());
        BackupVO parent = findById(backup.getParentId());
        VMInstanceVO vm = vmInstanceDao.findById(backup.getVMId());

        BackupResponse backupResponse = new BackupResponse();
        backupResponse.setId(backup.getUuid());
        backupResponse.setAccountId(account.getUuid());
        backupResponse.setUserId(user.getUuid());
        backupResponse.setName(backup.getName());
        backupResponse.setDescription(backup.getDescription());
        if (parent != null) {
            backupResponse.setParentId(parent.getUuid());
        }
        backupResponse.setVmId(vm.getUuid());
        if (CollectionUtils.isNotEmpty(backup.getVolumeIds())) {
            List<String> volIds = new ArrayList<>();
            for (Long volId : backup.getVolumeIds()) {
                VolumeVO volume = volumeDao.findById(volId);
                volIds.add(volume.getUuid());
            }
            backupResponse.setVolumeIds(volIds);
        }
        backupResponse.setStatus(backup.getStatus());
        backupResponse.setObjectName("backup");
        return backupResponse;
    }
}
