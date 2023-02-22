package org.apache.cloudstack.ipreservation;

import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.IpReservationVO;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.dao.IpReservationDao;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ComponentLifecycleBase;
import com.cloud.utils.net.NetUtils;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.ipreservation.api.commands.AddIpReservationCmd;
import org.apache.cloudstack.ipreservation.api.commands.GenerateIpReservationCmd;
import org.apache.cloudstack.ipreservation.api.commands.ListIpReservationCmd;
import org.apache.cloudstack.ipreservation.api.commands.RemoveIpReservationCmd;
import org.apache.cloudstack.ipreservation.api.response.IpReservationResponse;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IpReservationServiceImpl extends ComponentLifecycleBase implements IpReservationService {
    public static final Logger logger = Logger.getLogger(IpReservationServiceImpl.class);

    @Inject
    IpReservationDao ipReservationDao;

    @Inject
    NetworkService networkService;

    @Inject
    NetworkModel networkModel;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        return true;
    }

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(AddIpReservationCmd.class);
        cmdList.add(GenerateIpReservationCmd.class);
        cmdList.add(ListIpReservationCmd.class);
        cmdList.add(RemoveIpReservationCmd.class);
        return cmdList;
    }

    @Override
    public void createReservation(AddIpReservationCmd cmd) {
        logger.debug("Creating reservation for network: " + cmd.getNetworkId() + " with range: " + cmd.getStartIp() + "-" + cmd.getEndIp());
        Network network = networkService.getNetwork(cmd.getNetworkId());
        if (network == null) {
            throw new InvalidParameterValueException("Unable to find network: " + cmd.getNetworkId());
        }
        if (network.getCidr() == null) {
            throw new InvalidParameterValueException("Network does not currently have a CIDR to identify IPs in");
        }
        validateAddReservation(cmd, network);
        IpReservationVO create = new IpReservationVO(cmd.getStartIp(), cmd.getEndIp(), network.getId());
        IpReservationVO created = ipReservationDao.persist(create);

        IpReservationResponse ipReservationResponse = generateResponse(created, network.getUuid());
        ipReservationResponse.setResponseName(cmd.getCommandName());
        cmd.setResponseObject(ipReservationResponse);
    }

    protected static void validateAddReservation(AddIpReservationCmd cmd, Network network) {
        if (!NetUtils.validIpRange(cmd.getStartIp(), cmd.getEndIp())) {
            throw new InvalidParameterValueException("Start and end are not valid range");
        }

        Pair<String, Integer> cidr = NetUtils.getCidr(network.getCidr());
        if (!NetUtils.sameSubnetCIDR(cidr.first(), cmd.getStartIp(), cidr.second())) {
            throw new InvalidParameterValueException("Start is not within the network cidr");
        }

        if (!NetUtils.sameSubnetCIDR(cidr.first(), cmd.getEndIp(), cidr.second())) {
            throw new InvalidParameterValueException("End is not within the network cidr");
        }
    }

    @Override
    public void generateReservation(GenerateIpReservationCmd cmd) throws InsufficientVirtualNetworkCapacityException {
        logger.debug("Generating an IP Reservation for network: " + cmd.getNetworkId());
        Network network = networkService.getNetwork(cmd.getNetworkId());
        if (network == null) {
            throw new InvalidParameterValueException("Unable to find network: " + cmd.getNetworkId());
        }
        if (network.getCidr() == null) {
            throw new InvalidParameterValueException("Network does not currently have a CIDR to identify IPs in");
        }
        Long freeIp = networkModel.getAvailableIps(network, null)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InsufficientVirtualNetworkCapacityException("There is no free ip available on this network.",
                        Network.class,
                        network.getId()));

        String ip = NetUtils.long2Ip(freeIp);

        IpReservationVO create = new IpReservationVO(ip, ip, network.getId());
        IpReservationVO created = ipReservationDao.persist(create);

        IpReservationResponse response = generateResponse(created, network.getUuid());
        response.setResponseName(cmd.getCommandName());
        cmd.setResponseObject(response);
    }

    @Override
    public void getReservations(ListIpReservationCmd cmd) {
        if (cmd.getNetworkId() != null) {
            logger.debug("Getting all reservations for network " + cmd.getNetworkId());
            Network network = networkService.getNetwork(cmd.getNetworkId());
            if (network == null) {
                throw new InvalidParameterValueException("Unable to find network: " + cmd.getNetworkId());
            }
            List<IpReservationVO> reservations = ipReservationDao.getIpReservationsForNetwork(network.getId());
            ListResponse<IpReservationResponse> response = generateListResponse(reservations, network.getUuid());
            response.setResponseName(cmd.getCommandName());
            cmd.setResponseObject(response);
        } else {
            logger.debug("Getting all reservations");
            List<IpReservationDao.IpReservationWithNetwork> reservations = ipReservationDao.getAllIpReservations();
            List<IpReservationResponse> responses = reservations.stream()
                    .map(IpReservationResponse::new)
                    .collect(Collectors.toList());
            ListResponse<IpReservationResponse> response = new ListResponse<>();
            response.setResponses(responses);
            response.setResponseName(cmd.getCommandName());
            cmd.setResponseObject(response);
        }
    }

    @Override
    public void removeReservation(RemoveIpReservationCmd cmd) {
        logger.debug("Removing reservation: " + cmd.getId());
        IpReservationVO reservation = ipReservationDao.findByUuid(cmd.getId());
        boolean removed = true;
        if (reservation != null) {
            removed = ipReservationDao.remove(reservation.getId());
        }
        SuccessResponse response = new SuccessResponse(cmd.getCommandName());
        response.setSuccess(removed);
        cmd.setResponseObject(response);
    }

    protected IpReservationResponse generateResponse(IpReservationVO reservation, String networkUuid) {
        return new IpReservationResponse(reservation.getUuid(), reservation.getStartIp(), reservation.getEndIp(), networkUuid);
    }

    protected ListResponse<IpReservationResponse> generateListResponse(List<IpReservationVO> reservations, String networkUuid) {
        List<IpReservationResponse> responses = reservations.stream()
                .map(reservation -> generateResponse(reservation, networkUuid))
                .collect(Collectors.toList());
        ListResponse<IpReservationResponse> response = new ListResponse<>();
        response.setResponses(responses);
        return response;
    }
}
