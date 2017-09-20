/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.restcomm.connect.http;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.CONFLICT;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.OrganizationList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.dns.DnsProvisioningManager;
import org.restcomm.connect.dns.DnsProvisioningManagerProvider;
import org.restcomm.connect.http.converter.AccountConverter;
import org.restcomm.connect.http.converter.AccountListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManager;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManagerProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.xstream.XStream;

/**
 * @author maria.farooq@telestax.com (Maria Farooq)
 */
public class OrganizationsEndpoint extends SecuredEndpoint {
    @Context
    protected ServletContext context;
    protected DnsProvisioningManager dnsProvisioningManager;
    protected Configuration runtimeConfiguration;
    protected Configuration rootConfiguration; // top-level configuration element
    protected Gson gson;
    protected XStream xstream;

    public OrganizationsEndpoint() {
        super();
    }

    // used for testing
    public OrganizationsEndpoint(ServletContext context, HttpServletRequest request) {
        super(context,request);
    }

    @PostConstruct
    void init() {
        rootConfiguration = (Configuration) context.getAttribute(Configuration.class.getName());
        runtimeConfiguration = rootConfiguration.subset("runtime-settings");
        super.init(runtimeConfiguration);
        final AccountConverter converter = new AccountConverter(runtimeConfiguration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Account.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new AccountListConverter(runtimeConfiguration));
        xstream.registerConverter(new RestCommResponseConverter(runtimeConfiguration));
        // Make sure there is an authenticated account present when this endpoint is used
        // get manager from context or create it if it does not exist
        dnsProvisioningManager = new DnsProvisioningManagerProvider(configuration, context).get();
    }

    /**
     * @param organizationSid
     * @param responseType
     * @return
     */
    protected Response getOrganization(final String organizationSid, final MediaType responseType) {
        checkAuthenticatedAccount();
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        checkPermission("RestComm:Read:Organizations");
        Organization organization = null;

        if (!Sid.pattern.matcher(organizationSid).matches()) {
            return status(BAD_REQUEST).build();
        }else{
            try {
                //if account is not super admin then allow to read only affiliated organization
                if(!isSuperAdmin()){
                    if(userIdentityContext.getEffectiveAccount().getOrganizationSid().equals(new Sid(organizationSid))){
                        organization = organizationsDao.getOrganization(new Sid(organizationSid));
                    }else{
                        return status(FORBIDDEN).build();
                    }
                }else{
                    organization = organizationsDao.getOrganization(new Sid(organizationSid));
                }
            } catch (Exception e) {
                return status(NOT_FOUND).build();
            }
        }

        if (organization == null) {
            return status(NOT_FOUND).build();
        } else {
            if (APPLICATION_XML_TYPE == responseType) {
                final RestCommResponse response = new RestCommResponse(organization);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE == responseType) {
                return ok(gson.toJson(organization), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    /**
     * @param info
     * @param responseType
     * @return
     */
    protected Response getOrganizations(UriInfo info, final MediaType responseType) {
        checkAuthenticatedAccount();
        allowOnlySuperAdmin();

        List<Organization> organizations = null;

        String status = info.getQueryParameters().getFirst("Status");

        if(status != null && Organization.Status.getValueOf(status.toLowerCase()) != null){
            organizations = organizationsDao.getOrganizationsByStatus(Organization.Status.getValueOf(status.toLowerCase()));
        }else{
            organizations = organizationsDao.getAllOrganizations();
        }
        if (organizations == null || organizations.isEmpty()) {
            return status(NOT_FOUND).build();
        }
        if (APPLICATION_XML_TYPE == responseType) {
            final RestCommResponse response = new RestCommResponse(new OrganizationList(organizations));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE == responseType) {
            return ok(gson.toJson(organizations), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

	/**
	 * putOrganization
	 * @param domainName
	 * @param data
	 * @param applicationJsonType
	 * @return
	 */
    protected Response putOrganization(String domainName, MultivaluedMap<String, String> data,
			MediaType responseType) {
    	if(domainName == null){
            return status(BAD_REQUEST).entity("domain name can not be empty. Please, choose a valid name and try again.").build();
        }else{
        	checkAuthenticatedAccount();
            allowOnlySuperAdmin();

            //Character verification
            final Pattern pattern = Pattern.compile("[A-Za-z0-9\\-]{1,255}");
            if(!pattern.matcher(domainName).matches()){
                return status(BAD_REQUEST).entity("Total Length of domain_name can be upto 255 Characters. It can contain only letters, number and hyphen - sign.. Please, choose a valid name and try again.").build();
            }

            //Check if domain_name does not already taken inside restcomm by an organization.
            Organization organization = organizationsDao.getOrganizationByDomainName(domainName);
            if(organization != null){
            	return status(CONFLICT)
                        .entity("This domain name is not available. Please, choose a different name and try again.")
                        .build();
            }
            //restcomm.com
            data.get("HostedZone");
            //TODO: dns get, check if its not already taken
        }
	}
}
