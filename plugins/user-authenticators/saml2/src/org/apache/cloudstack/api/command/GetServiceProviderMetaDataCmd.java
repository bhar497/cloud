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
package org.apache.cloudstack.api.command;

import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.user.Account;
import com.cloud.utils.HttpUtils;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ApiServerService;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.auth.APIAuthenticationType;
import org.apache.cloudstack.api.auth.APIAuthenticator;
import org.apache.cloudstack.api.auth.PluggableAPIAuthenticator;
import org.apache.cloudstack.api.response.SAMLMetaDataResponse;
import org.apache.cloudstack.saml.SAML2AuthManager;
import org.apache.cloudstack.saml.SAMLProviderMetadata;
import org.apache.log4j.Logger;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.io.MarshallingException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.net.InetAddress;

@APICommand(name = "getSPMetadata", description = "Returns SAML2 CloudStack Service Provider MetaData", responseObject = SAMLMetaDataResponse.class, entityType = {})
public class GetServiceProviderMetaDataCmd extends BaseCmd implements APIAuthenticator {
    public static final Logger s_logger = Logger.getLogger(GetServiceProviderMetaDataCmd.class.getName());
    private static final String s_name = "spmetadataresponse";

    @Inject
    ApiServerService _apiServer;

    SAML2AuthManager _samlAuthManager;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_TYPE_NORMAL;
    }

    @Override
    public void execute() throws ServerApiException {
        throw new ServerApiException(ApiErrorCode.METHOD_NOT_ALLOWED, "This is an authentication plugin api, cannot be used directly");
    }

    @Override
    public String authenticate(String command, Map<String, Object[]> params, HttpSession session, InetAddress remoteAddress, String responseType, StringBuilder auditTrailSb, final HttpServletRequest req, final HttpServletResponse resp) throws ServerApiException {
        SAMLMetaDataResponse response = new SAMLMetaDataResponse();
        response.setResponseName(getCommandName());

        try {
            DefaultBootstrap.bootstrap();
        } catch (ConfigurationException | FactoryConfigurationError e) {
            s_logger.error("OpenSAML Bootstrapping error: " + e.getMessage());
            throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(),
                    "OpenSAML Bootstrapping error while creating SP MetaData",
                    params, responseType));
        }

        final SAMLProviderMetadata spMetadata = _samlAuthManager.getSPMetadata();

        EntityDescriptor spEntityDescriptor = _samlAuthManager.getEntityDescriptor(spMetadata);

        StringWriter stringWriter = new StringWriter();
        try {
            _samlAuthManager.getDescriptorXmlString(spEntityDescriptor, stringWriter);
            response.setMetadata(stringWriter.toString());
        } catch (ParserConfigurationException | IOException | MarshallingException | TransformerException e) {
            if (responseType.equals(HttpUtils.JSON_CONTENT_TYPE)) {
                response.setMetadata("Error creating Service Provider MetaData XML: " + e.getMessage());
            } else {
                return "Error creating Service Provider MetaData XML: " + e.getMessage();
            }
        }
        // For JSON type return serialized response object
        if (responseType.equals(HttpUtils.RESPONSE_TYPE_JSON)) {
            return ApiResponseSerializer.toSerializedString(response, responseType);
        }
        // For other response types return XML
        return stringWriter.toString();
    }

    @Override
    public APIAuthenticationType getAPIType() {
        return APIAuthenticationType.READONLY_API;
    }

    @Override
    public void setAuthenticators(List<PluggableAPIAuthenticator> authenticators) {
        for (PluggableAPIAuthenticator authManager: authenticators) {
            if (authManager != null && authManager instanceof SAML2AuthManager) {
                _samlAuthManager = (SAML2AuthManager) authManager;
            }
        }
        if (_samlAuthManager == null) {
            s_logger.error("No suitable Pluggable Authentication Manager found for SAML2 getSPMetadata Cmd");
        }
    }
}
