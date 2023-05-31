package com.cloud.network.dao;

import com.cloud.network.IpReservationVO;
import com.cloud.utils.db.GenericDao;

import java.util.List;

public interface IpReservationDao extends GenericDao<IpReservationVO, Long> {
    List<IpReservationVO> getIpReservationsForNetwork(long networkId);

    List<IpReservationWithNetwork> getAllIpReservations();

    class IpReservationWithNetwork {
        public String id;
        public String startIp;
        public String endIp;
        public String networkId;

        public IpReservationWithNetwork(String id, String startIp, String endIp, String networkId) {
            this.id = id;
            this.startIp = startIp;
            this.endIp = endIp;
            this.networkId = networkId;
        }
    }
}
