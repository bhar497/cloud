package org.apache.cloudstack.ipreservation.api.commands;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.ipreservation.IpReservationService;
import org.apache.cloudstack.ipreservation.api.response.IpReservationResponse;

import javax.inject.Inject;

@APICommand(name = "generateIpReservation", description = "Generate a IP Reservation and return the reserved IP", responseObject = IpReservationResponse.class,
        responseHasSensitiveInfo = false, requestHasSensitiveInfo = false, since = "4.11.3.1")
public class GenerateIpReservationCmd extends BaseCmd {
    @Inject
    IpReservationService ipReservationService;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, required = true, entityType = NetworkResponse.class, description = "Network ID")
    private Long networkId;

    public Long getNetworkId() {
        return networkId;
    }

    @Override
    public void execute() throws ServerApiException, ConcurrentOperationException {
        try {
            ipReservationService.generateReservation(this);
        } catch (InvalidParameterValueException | InsufficientVirtualNetworkCapacityException ex) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, ex.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return "generateipreservationresponse";
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
