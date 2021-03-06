/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.restcomm.connect.telephony.api;

import org.restcomm.connect.commons.dao.Sid;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class UserRegistration {
    private final String user;
    private final String address;
    private final Boolean registered;
    private final Sid organizationsSid;

    public UserRegistration(final String user, final String address, final Boolean registered, final Sid organizationsSid) {
        this.user = user;
        this.address = address;
        this.registered = registered;
        this.organizationsSid = organizationsSid;
    }

    public String getUser() {
        return user;
    }

    public String getAddress() {
        return address;
    }

    public Boolean getRegistered() {
        return registered;
    }

    public Sid getOrganizationsSid() {
        return organizationsSid;
    }

    public String getUserPlusOrganizationsSid() {
        return user+":"+organizationsSid;
    }
}
