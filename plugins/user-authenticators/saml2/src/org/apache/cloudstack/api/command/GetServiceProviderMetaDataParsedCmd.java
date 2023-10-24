package org.apache.cloudstack.api.command;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.serializer.Param;
import com.cloud.user.Account;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.saml.SAML2AuthManager;
import org.apache.cloudstack.saml.SAMLProviderMetadata;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.xml.io.MarshallingException;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;

@APICommand(name="getSPMetadataParsed", description = "Returns SAML2 Cloudstack Service Provider Metadata parsed out", responseObject = GetServiceProviderMetaDataParsedCmd.SPMetaDataParsedResponse.class)
public class GetServiceProviderMetaDataParsedCmd extends BaseCmd {

    private static final String s_name = "spmetadataparsedresponse";

    @Inject
    SAML2AuthManager samlAuthManager;

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        SAMLProviderMetadata metadata = samlAuthManager.getSPMetadata();
        Date notAfter = metadata.getSigningCertificate().getNotAfter();
        Date notBefore = metadata.getSigningCertificate().getNotBefore();
        String certString = metadata.getSigningCertificate().toString();
        String entityId = metadata.getEntityId();
        String ssoUrl = metadata.getSsoUrl();
        String sloUrl = metadata.getSloUrl();
        String contactName = metadata.getContactPersonName();
        String contactEmail = metadata.getContactPersonEmail();
        String orgUrl = metadata.getOrganizationUrl();
        String orgName = metadata.getOrganizationName();

        String descriptorXml;

        EntityDescriptor spEntityDescriptor = samlAuthManager.getEntityDescriptor(metadata);
        StringWriter stringWriter = new StringWriter();
        try {
            samlAuthManager.getDescriptorXmlString(spEntityDescriptor, stringWriter);
            descriptorXml = stringWriter.toString();
        } catch (ParserConfigurationException | IOException | MarshallingException | TransformerException e) {
            descriptorXml = "Error creating Service Provider MetaData XML: " + e.getMessage();
        }

        SPMetaDataParsedResponse responseObject = new SPMetaDataParsedResponse(notBefore, notAfter, certString, entityId, ssoUrl, sloUrl, contactName, contactEmail, orgUrl, orgName, descriptorXml);
        responseObject.setResponseName(s_name);
        setResponseObject(responseObject);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    public static class SPMetaDataParsedResponse extends BaseResponse {
        @SerializedName("certificatenotbefore")
        @Param(description = "The Certificate Start Date")
        private Date notBefore;

        @SerializedName("certificatenotafter")
        @Param(description = "The Certificate End Date")
        private Date notAfter;

        @SerializedName("certstring")
        @Param(description = "The certificate string")
        private String certString;

        @SerializedName("entityid")
        private String entityId;

        @SerializedName("ssourl")
        private String ssoUrl;

        @SerializedName("slourl")
        private String sloUrl;

        @SerializedName("contactname")
        private String contactName;

        @SerializedName("contactemail")
        private String contactEmail;

        @SerializedName("orgurl")
        private String orgUrl;

        @SerializedName("orgname")
        private String orgName;

        @SerializedName("descriptorxml")
        private String descriptorXml;

        public SPMetaDataParsedResponse(Date notBefore, Date notAfter, String certString, String entityId, String ssoUrl, String sloUrl, String contactName, String contactEmail, String orgUrl, String orgName, String descriptorXml) {
            super();
            this.setResponseName(s_name);
            this.setObjectName(s_name);
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.certString = certString;
            this.entityId = entityId;
            this.ssoUrl = ssoUrl;
            this.sloUrl = sloUrl;
            this.contactName = contactName;
            this.contactEmail = contactEmail;
            this.orgUrl = orgUrl;
            this.orgName = orgName;
            this.descriptorXml = descriptorXml;
        }
    }
}
