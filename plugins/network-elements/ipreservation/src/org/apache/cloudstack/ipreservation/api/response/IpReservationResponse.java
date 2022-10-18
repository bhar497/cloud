package org.apache.cloudstack.ipreservation.api.response;

import com.cloud.network.dao.IpReservationDao;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;
import com.cloud.network.IpReservationVO;

@EntityReference(value = IpReservationVO.class)
public class IpReservationResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    private String id;

    @SerializedName(ApiConstants.START_IP)
    private String startIp;

    @SerializedName(ApiConstants.END_IP)
    private String endIp;

    @SerializedName(ApiConstants.NETWORK_ID)
    private String networkId;

    public IpReservationResponse() {
    }

    public IpReservationResponse(String id, String startIp, String endIp, String networkId) {
        super("ipreservation");
        this.id = id;
        this.startIp = startIp;
        this.endIp = endIp;
        this.networkId = networkId;
    }

    public IpReservationResponse(IpReservationDao.IpReservationWithNetwork reservation) {
        super("ipreservation");
        this.id = reservation.id;
        this.startIp = reservation.startIp;
        this.endIp = reservation.endIp;
        this.networkId = reservation.networkId;
    }
}
