package org.apache.cloudstack.api.command;

import com.cloud.user.Account;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.response.SAMLMetaDataResponse;
import org.apache.cloudstack.saml.SAML2AuthManager;
import org.apache.cloudstack.saml.SAMLProviderMetadata;
import org.apache.log4j.Logger;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.xml.io.MarshallingException;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringWriter;

@APICommand(name = "renewSPCertificates", description = "Renews the internal certificate used for SAML communication with the IDP", responseObject = SAMLMetaDataResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class RenewServiceProviderCertificateCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(RenewServiceProviderCertificateCmd.class.getName());
    private static final String s_name = "renewspcertificateresponse";

    @Inject
    SAML2AuthManager saml2AuthManager;

    @Override
    public void execute() {
        SAMLProviderMetadata spMetadata = saml2AuthManager.renewCertificates();

        SAMLMetaDataResponse response = new SAMLMetaDataResponse();
        response.setResponseName(getCommandName());

        EntityDescriptor spEntityDescriptor = saml2AuthManager.getEntityDescriptor(spMetadata);

        StringWriter stringWriter = new StringWriter();
        try {
            saml2AuthManager.getDescriptorXmlString(spEntityDescriptor, stringWriter);
            response.setMetadata(stringWriter.toString());
        } catch (ParserConfigurationException | IOException | MarshallingException | TransformerException e) {
            response.setMetadata("Error creating Service Provider MetaData XML: " + e.getMessage());
        }
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
