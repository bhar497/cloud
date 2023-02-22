package org.apache.cloudstack.ipreservation.api.commands;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.ipreservation.IpReservationService;
import org.apache.cloudstack.ipreservation.api.response.IpReservationResponse;

import javax.inject.Inject;

@APICommand(name = "listIpReservations", description = "List IP Reservations", responseObject = IpReservationResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
        since = "4.11.3.1")
public class ListIpReservationCmd extends BaseListCmd {

    @Inject
    IpReservationService ipReservationService;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, description = "Network ID")
    Long networkId;

    public Long getNetworkId() {
        return networkId;
    }

    @Override
    public void execute() throws ServerApiException, ConcurrentOperationException {
        try {
            ipReservationService.getReservations(this);
        } catch (InvalidParameterValueException ex) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, ex.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return "listipreservationsresponse";
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
