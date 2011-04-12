/**
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004, 2005, 2006], VMware, Inc.
 * This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.plugin.mgmt.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hyperic.hibernate.ContainerManagedTimestampTrackable;

/**
 * Plugin generated by hbm2java
 */
@Entity
@Table(name = "EAM_PLUGIN")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Plugin implements ContainerManagedTimestampTrackable, Serializable {

    @Column(name = "CTIME", nullable = false)
    private long creationTime;

    @Id
    @GenericGenerator(name = "mygen1", strategy = "increment")
    @GeneratedValue(generator = "mygen1")
    @Column(name = "ID")
    private Integer id;

    @Column(name = "MD5", length = 100, nullable = false)
    private String md5;

    @Column(name = "NAME", unique = true, length = 200, nullable = false)
    private String name;

    @Column(name = "PATH", length = 500, nullable = false)
    private String path;

    @Column(name = "VERSION_COL", nullable = false)
    @Version
    private Long version;

    /**
     * default constructor
     */
    public Plugin() {
        super();
    }

    public Plugin(String name, String path, String md5) {
        this.name = name;
        this.path = path;
        this.md5 = md5;
    }

    /**
     * @see org.hyperic.hibernate.ContainerManagedTimestampTrackable#allowContainerManagedLastModifiedTime()
     * @return <code>true</code> by default.
     */
    public boolean allowContainerManagedCreationTime() {
        return true;
    }

    /**
     * @see org.hyperic.hibernate.ContainerManagedTimestampTrackable#allowContainerManagedLastModifiedTime()
     * @return <code>false</code> by default.
     */
    public boolean allowContainerManagedLastModifiedTime() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Plugin other = (Plugin) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    /**
     * @deprecated use getCreationTime()
     */
    public long getCtime() {
        return getCreationTime();
    }

    public Integer getId() {
        return id;
    }

    public String getMD5() {
        return this.md5;
    }

    // Property accessors
    public String getName() {
        return this.name;
    }

    public String getPath() {
        return this.path;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    public void setCreationTime(long ctime) {
        this.creationTime = ctime;
    }

    /**
     * @deprecated use setCreationTime()
     * @param cTime
     */
    public void setCtime(long cTime) {
        setCreationTime(cTime);
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setMD5(String md5) {
        this.md5 = md5;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    protected void setVersion(Long newVer) {
        version = newVer;
    }

}
